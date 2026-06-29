package io.github.posseidon.core.ingest.processor;

import io.github.posseidon.core.model.FileMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Function;

/**
 * Thread-safe: {@link HttpClient} is designed for concurrent use; all fields are final.
 *
 * <p>Usage with a chain of consumers:
 * <pre>{@code
 * MetadataConsumer pipeline = new RestMetadataPublisher(endpoint, objectMapper::writeValueAsString)
 *         .andThen(auditLogger);
 *
 * FileProcessor processor = new MetadataExtractionProcessor(extractor, pipeline);
 * new FileDiscoveryOrchestrator(processor).discoverAndProcess(root);
 * }</pre>
 */
public final class RestMetadataPublisher implements MetadataConsumer {

    private static final Logger log = LoggerFactory.getLogger(RestMetadataPublisher.class);

    private final URI endpoint;
    private final Function<FileMetadata, String> serializer;
    private final HttpClient httpClient;

    public RestMetadataPublisher(URI endpoint, Function<FileMetadata, String> serializer) {
        this.endpoint = endpoint;
        this.serializer = serializer;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void accept(FileMetadata metadata) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serializer.apply(metadata)))
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("POST {} → HTTP {}", endpoint, response.statusCode());
        }
    }
}
