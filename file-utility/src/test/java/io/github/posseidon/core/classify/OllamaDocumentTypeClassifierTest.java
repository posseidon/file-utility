package io.github.posseidon.core.classify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.posseidon.core.model.DocumentInsights;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaDocumentTypeClassifierTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private URI endpoint;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/api/generate");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // -------------------------------------------------------------------------
    // Full JSON response
    // -------------------------------------------------------------------------

    @Test
    void fullJsonResponsePopulatesAllFields() throws Exception {
        String inner = """
                {
                  "document_type": "Invoice",
                  "language": "en",
                  "summary": "Invoice from Acme Corp for $100.",
                  "tags": ["payment", "vendor"],
                  "action_required": true,
                  "action_deadline": "2024-02-01",
                  "key_dates": {"issue_date": "2024-01-01", "due_date": "2024-02-01"},
                  "amounts": ["total: $100.00"],
                  "organizations": ["Acme Corp"],
                  "people": ["John Doe"],
                  "contains_pii": true
                }
                """;
        respondWith(200, ollamaResponse(inner));
        var analyzer = new OllamaDocumentTypeClassifier(endpoint, "llama3", 4_000);

        DocumentInsights insights = analyzer.analyzeAsync("INVOICE\nDate: 2024-01-01\nAmount: $100").get();

        assertThat(insights.documentType()).isEqualTo("invoice");
        assertThat(insights.language()).isEqualTo("en");
        assertThat(insights.summary()).isEqualTo("Invoice from Acme Corp for $100.");
        assertThat(insights.tags()).containsExactly("payment", "vendor");
        assertThat(insights.actionRequired()).isTrue();
        assertThat(insights.actionDeadline()).isEqualTo("2024-02-01");
        assertThat(insights.keyDates())
                .containsEntry("issue_date", "2024-01-01")
                .containsEntry("due_date", "2024-02-01");
        assertThat(insights.amounts()).containsExactly("total: $100.00");
        assertThat(insights.organizations()).containsExactly("Acme Corp");
        assertThat(insights.people()).containsExactly("John Doe");
        assertThat(insights.containsPii()).isTrue();
    }

    private void respondWith(int status, String responseBody) {
        server.createContext("/api/generate", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {os.write(bytes);}
        });
        server.start();
    }

    private static String ollamaResponse(String inner) {
        try {
            return "{\"response\":" + MAPPER.writeValueAsString(inner) + ",\"done\":true}";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void documentTypeIsNormalizedToLowercaseSnakeCase() throws Exception {
        respondWith(200, ollamaResponse("{\"document_type\":\"Utility Bill\",\"tags\":[],\"amounts\":[]," +
                "\"organizations\":[],\"people\":[],\"key_dates\":{}}"));
        var analyzer = new OllamaDocumentTypeClassifier(endpoint, "llama3", 4_000);

        assertThat(analyzer.analyzeAsync("bill text").get().documentType()).isEqualTo("utility_bill");
    }

    // -------------------------------------------------------------------------
    // Fallback behaviour
    // -------------------------------------------------------------------------

    @Test
    void nullJsonFieldsAreReturnedAsNull() throws Exception {
        respondWith(200, ollamaResponse("{\"document_type\":\"invoice\",\"language\":null,\"action_deadline\":null," +
                "\"tags\":[],\"amounts\":[],\"organizations\":[],\"people\":[],\"key_dates\":{}}"));
        var analyzer = new OllamaDocumentTypeClassifier(endpoint, "llama3", 4_000);

        DocumentInsights insights = analyzer.analyzeAsync("text").get();

        assertThat(insights.language()).isNull();
        assertThat(insights.actionDeadline()).isNull();
    }

    // -------------------------------------------------------------------------
    // Request body
    // -------------------------------------------------------------------------

    @Test
    void markdownFencedJsonIsParsedCorrectly() throws Exception {
        String inner = "```json\n{\"document_type\":\"bank_statement\",\"tags\":[],\"amounts\":[]," +
                "\"organizations\":[],\"people\":[],\"key_dates\":{}}\n```";
        respondWith(200, ollamaResponse(inner));
        var analyzer = new OllamaDocumentTypeClassifier(endpoint, "llama3", 4_000);

        assertThat(analyzer.analyzeAsync("statement text").get().documentType()).isEqualTo("bank_statement");
    }

    @Test
    void nonJsonResponseFallsBackToDocumentTypeOnly() throws Exception {
        respondWith(200, ollamaResponse("  Invoice\n"));
        var analyzer = new OllamaDocumentTypeClassifier(endpoint, "llama3", 4_000);

        DocumentInsights insights = analyzer.analyzeAsync("INVOICE text").get();

        assertThat(insights.documentType()).isEqualTo("invoice");
        assertThat(insights.language()).isNull();
        assertThat(insights.summary()).isNull();
        assertThat(insights.tags()).isEmpty();
        assertThat(insights.actionRequired()).isFalse();
        assertThat(insights.containsPii()).isFalse();
    }

    @Test
    void requestBodyContainsTemperatureZeroAndStreamFalse() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        captureBody(body, "{\"document_type\":\"invoice\",\"tags\":[],\"amounts\":[],\"organizations\":[]," +
                "\"people\":[],\"key_dates\":{}}");
        var analyzer = new OllamaDocumentTypeClassifier(endpoint, "llama3", 4_000);

        analyzer.analyzeAsync("some text").get();

        assertThat(body.get())
                .contains("\"temperature\":0.0")
                .contains("\"stream\":false");
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    private void captureBody(AtomicReference<String> captured, String innerJson) throws Exception {
        server.createContext("/api/generate", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = ollamaResponse(innerJson).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {os.write(resp);}
        });
        server.start();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Test
    void textIsTruncatedToMaxContextChars() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        captureBody(body, "{\"document_type\":\"invoice\",\"tags\":[],\"amounts\":[],\"organizations\":[]," +
                "\"people\":[],\"key_dates\":{}}");
        var analyzer = new OllamaDocumentTypeClassifier(endpoint, "llama3", 10);

        analyzer.analyzeAsync("A".repeat(200)).get();

        assertThat(body.get()).contains("A".repeat(10));
        assertThat(body.get()).doesNotContain("A".repeat(11));
    }

    @Test
    void modelNameIsIncludedInRequestBody() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        captureBody(body, "{\"document_type\":\"invoice\",\"tags\":[],\"amounts\":[],\"organizations\":[]," +
                "\"people\":[],\"key_dates\":{}}");
        var analyzer = new OllamaDocumentTypeClassifier(endpoint, "mistral", 4_000);

        analyzer.analyzeAsync("text").get();

        assertThat(body.get()).contains("\"model\":\"mistral\"");
    }

    @Test
    void http500CompletesExceptionally() {
        respondWith(500, "Internal Server Error");
        var analyzer = new OllamaDocumentTypeClassifier(endpoint, "llama3", 4_000);

        assertThatThrownBy(() -> analyzer.analyzeAsync("text").get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(OllamaClassificationException.class)
                .hasMessageContaining("500");
    }
}
