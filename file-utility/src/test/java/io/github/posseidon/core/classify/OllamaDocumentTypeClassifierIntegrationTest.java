package io.github.posseidon.core.classify;

import io.github.posseidon.core.model.DocumentInsights;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.net.http.HttpClient.newHttpClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class OllamaDocumentTypeClassifierIntegrationTest {

    private static final URI OLLAMA_ENDPOINT = URI.create("http://localhost:11434/api/generate");
    private static final String MODEL = "mistral:7b";
    private static final Path DOWNLOADS = Path.of(System.getProperty("user.home"), "Downloads");
    private static final int MAX_CONTEXT_CHARS = 4_000;

    @BeforeAll
    static void requiresRunningOllamaAndDownloads() {
        assumeTrue(isOllamaReachable(), "Ollama is not reachable at http://localhost:11434 — start it first");
        assumeTrue(Files.isDirectory(DOWNLOADS), "~/Downloads directory does not exist");
    }

    private static boolean isOllamaReachable() {
        try {
            HttpResponse<Void> r = newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:11434"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return r.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Test
    void analyzesFilesInDownloadsFolder() throws IOException {
        var analyzer = new OllamaDocumentTypeClassifier(OLLAMA_ENDPOINT, MODEL, MAX_CONTEXT_CHARS);

        List<Path> allFiles;
        try (Stream<Path> stream = Files.list(DOWNLOADS)) {
            allFiles = stream.filter(Files::isRegularFile).toList();
        }
        assumeFalse(allFiles.isEmpty(), "~/Downloads contains no regular files");

        Map<Path, CompletableFuture<DocumentInsights>> futures = new LinkedHashMap<>();
        for (Path file : allFiles) {
            String text = extractText(file);
            if (!text.isBlank()) {
                futures.put(file,
                        analyzer.analyzeAsync(text)
                                .exceptionally(ex -> DocumentInsights.ofDocumentType("<error: " + ex.getMessage() +
                                        ">")));
            }
        }

        System.out.printf("%n%-50s  %-25s  %s%n", "FILE", "TYPE", "SUMMARY");
        System.out.println("─".repeat(110));

        futures.forEach((path, future) -> {
            DocumentInsights insights = future.join();
            System.out.printf("%-50s  %-25s  %s%n",
                    abbreviate(path.getFileName().toString(), 50),
                    abbreviate(insights.documentType(), 25),
                    abbreviate(insights.summary() != null ? insights.summary() : "", 50));

            if (!insights.documentType().startsWith("<")) {
                assertThat(insights.documentType())
                        .as("document type for %s must be lowercase_snake_case", path.getFileName())
                        .matches("[a-z][a-z0-9_]*");
            }
        });

        System.out.printf("%nAnalyzed %d / %d files (%d had no extractable text).%n",
                futures.size(), allFiles.size(), allFiles.size() - futures.size());
    }

    private static String extractText(Path path) {
        BodyContentHandler handler = new BodyContentHandler(MAX_CONTEXT_CHARS);
        try (TikaInputStream in = TikaInputStream.get(path)) {
            new AutoDetectParser().parse(in, handler, new Metadata(), new ParseContext());
        } catch (Exception e) {
            // Write-limit exceeded or unsupported format — use whatever was captured.
        }
        return handler.toString().strip();
    }

    private static String abbreviate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
