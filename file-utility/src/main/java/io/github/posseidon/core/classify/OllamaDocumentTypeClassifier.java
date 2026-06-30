package io.github.posseidon.core.classify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.posseidon.core.model.DocumentInsights;
import io.github.posseidon.core.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Extracts structured {@link DocumentInsights} from document text via a local Ollama instance.
 *
 * <p>Three key guarantees:
 * <ul>
 *   <li><b>Context truncation</b>: only the first {@code maxContextChars} chars are sent.</li>
 *   <li><b>Deterministic inference</b>: {@code temperature=0.0} is always enforced.</li>
 *   <li><b>Defensive fallback</b>: if the model returns invalid JSON, a degraded
 *       {@link DocumentInsights#ofDocumentType} instance is returned instead of throwing.</li>
 * </ul>
 *
 * <p>Thread-safe: {@link HttpClient} and {@link ObjectMapper} are shared across calls.
 */
public final class OllamaDocumentTypeClassifier implements DocumentTypeClassifier {

    private static final Logger log = LoggerFactory.getLogger(OllamaDocumentTypeClassifier.class);

    private static final String ANALYSIS_PROMPT =
            """
                    You are a document analysis assistant.
                    
                    TASK:
                    Analyze the document text and extract structured information.
                    
                    OUTPUT:
                    Return ONLY a valid JSON object — no prose, no markdown fences, no explanation.
                    
                    SCHEMA:
                    {
                      "document_type": "<lowercase_snake_case type, e.g. invoice, utility_bill, bank_statement>",
                      "language": "<ISO 639-1 code, e.g. en, hu, de>",
                      "summary": "<one factual sentence describing the document>",
                      "tags": ["<keyword>"],
                      "action_required": <true or false>,
                      "action_deadline": "<YYYY-MM-DD or null>",
                      "key_dates": {"<label>": "<YYYY-MM-DD>"},
                      "amounts": ["<label: value currency>"],
                      "organizations": ["<organization name>"],
                      "people": ["<person name>"],
                      "contains_pii": <true or false>
                    }
                    
                    RULES:
                    - document_type: most specific applicable type
                    - language: detect from document text
                    - tags: 3 to 8 searchable keywords
                    - action_required: true only if the reader must pay, sign, or respond
                    - action_deadline: ISO date if a deadline exists, otherwise null
                    - key_dates: all named dates (issue_date, due_date, expiry_date, contract_start, etc.)
                    - amounts: monetary values with a short label
                    - contains_pii: true if the document contains personal identifiers, addresses, or financial account numbers
                    
                    INPUT:
                    %s
                    
                    JSON:
                    """;

    private final URI endpoint;
    private final String model;
    private final int maxContextChars;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OllamaDocumentTypeClassifier(URI endpoint, String model, int maxContextChars) {
        this.endpoint = endpoint;
        this.model = model;
        this.maxContextChars = maxContextChars;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public CompletableFuture<DocumentInsights> analyzeAsync(String text) {
        String truncated = text.length() > maxContextChars ? text.substring(0, maxContextChars) : text;
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(truncated)))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new OllamaClassificationException(
                                "Ollama returned HTTP " + response.statusCode());
                    }
                    return parseResponse(response.body());
                })
                .whenComplete((insights, ex) -> {
                    if (ex != null) log.warn("Document analysis failed: {}", ex.getMessage());
                });
    }

    private String buildRequestBody(String truncatedText) {
        ObjectNode node = mapper.createObjectNode();
        node.put("model", model);
        node.put("prompt", ANALYSIS_PROMPT.formatted(truncatedText));
        node.put("temperature", 0.0);
        node.put("stream", false);
        return node.toString();
    }

    private DocumentInsights parseResponse(String responseBody) {
        String rawResponse = "";
        try {
            JsonNode root = mapper.readTree(responseBody);
            rawResponse = root.path("response").asText("");
            return parseInsights(stripFences(rawResponse.strip()));
        } catch (Exception e) {
            log.debug("Full JSON parse failed, falling back to document type only: {}", e.getMessage());
            String firstLine = rawResponse.strip().lines().findFirst().orElse("");
            String docType = StringUtility.normalize(firstLine);
            return DocumentInsights.ofDocumentType(docType.isEmpty() ? "other" : docType);
        }
    }

    private DocumentInsights parseInsights(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        return new DocumentInsights(
                StringUtility.normalize(node.path("document_type").asText("other")),
                nullableText(node.path("language")),
                nullableText(node.path("summary")),
                toStringList(node.path("tags")),
                node.path("action_required").asBoolean(false),
                nullableText(node.path("action_deadline")),
                toStringMap(node.path("key_dates")),
                toStringList(node.path("amounts")),
                toStringList(node.path("organizations")),
                toStringList(node.path("people")),
                node.path("contains_pii").asBoolean(false)
        );
    }

    private String stripFences(String s) {
        if (!s.startsWith("```")) return s;
        int firstNewline = s.indexOf('\n');
        if (firstNewline < 0) return s;
        s = s.substring(firstNewline + 1);
        int lastFence = s.lastIndexOf("```");
        if (lastFence >= 0) s = s.substring(0, lastFence);
        return s.strip();
    }

    private String nullableText(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        String value = node.asText("").strip();
        return value.isEmpty() || value.equalsIgnoreCase("null") ? null : value;
    }

    private List<String> toStringList(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(n -> {
            String s = n.asText("").strip();
            if (!s.isEmpty()) result.add(s);
        });
        return result;
    }

    private Map<String, String> toStringMap(JsonNode node) {
        if (!node.isObject()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> {
            String value = e.getValue().asText("").strip();
            if (!value.isEmpty() && !value.equalsIgnoreCase("null")) {
                result.put(e.getKey(), value);
            }
        });
        return Map.copyOf(result);
    }
}
