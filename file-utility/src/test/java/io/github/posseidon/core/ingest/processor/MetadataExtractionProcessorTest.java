package io.github.posseidon.core.ingest.processor;

import io.github.posseidon.core.detect.ContentTypeDetector;
import io.github.posseidon.core.hash.Sha256Hash;
import io.github.posseidon.core.ingest.MetadataExtractor;
import io.github.posseidon.core.model.FileMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataExtractionProcessorTest {

    private final MetadataExtractor extractor =
            new MetadataExtractor(new Sha256Hash(), new ContentTypeDetector());

    // -------------------------------------------------------------------------
    // Consumer delivery
    // -------------------------------------------------------------------------

    @Test
    void extractedMetadataIsDeliveredToConsumer(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = Files.writeString(tmp.resolve("hello.txt"), "hello");
        List<FileMetadata> captured = new CopyOnWriteArrayList<>();

        new MetadataExtractionProcessor(extractor, captured::add).process(file);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).path()).isEqualTo(file);
    }

    @Test
    void consumerReceivesFullyPopulatedMetadata(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = Files.writeString(tmp.resolve("data.txt"), "content");
        List<FileMetadata> captured = new CopyOnWriteArrayList<>();

        new MetadataExtractionProcessor(extractor, captured::add).process(file);

        FileMetadata meta = captured.get(0);
        assertThat(meta.name()).isEqualTo("data.txt");
        assertThat(meta.extension()).isEqualTo("txt");
        assertThat(meta.size()).isEqualTo(Files.size(file));
        assertThat(meta.sha256()).hasSize(64);
        assertThat(meta.mimeType()).isNotBlank();
        assertThat(meta.origin()).isNotBlank();
    }

    @Test
    void consumerIsCalledExactlyOnce(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = Files.writeString(tmp.resolve("once.txt"), "x");
        List<FileMetadata> captured = new CopyOnWriteArrayList<>();

        new MetadataExtractionProcessor(extractor, captured::add).process(file);

        assertThat(captured).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Exception propagation
    // -------------------------------------------------------------------------

    @Test
    void ioExceptionFromMissingFilePropagates(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist.txt");

        assertThatThrownBy(() ->
                new MetadataExtractionProcessor(extractor, meta -> {}).process(missing))
                .isInstanceOf(IOException.class);
    }

    @Test
    void ioExceptionFromConsumerPropagates(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("file.txt"), "x");
        MetadataConsumer failing = meta -> {throw new IOException("sink failure");};

        assertThatThrownBy(() ->
                new MetadataExtractionProcessor(extractor, failing).process(file))
                .isInstanceOf(IOException.class)
                .hasMessage("sink failure");
    }

    // -------------------------------------------------------------------------
    // Thread safety
    // -------------------------------------------------------------------------

    @Test
    void concurrentCallsOnSameInstanceProduceCorrectResults(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("shared.txt"), "concurrent");
        List<FileMetadata> results = new CopyOnWriteArrayList<>();
        MetadataExtractionProcessor processor = new MetadataExtractionProcessor(extractor, results::add);
        int threadCount = 20;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.invokeAll(Collections.nCopies(threadCount, processor.bind(file)));
        }

        assertThat(results)
                .hasSize(threadCount)
                .allSatisfy(m -> assertThat(m.path()).isEqualTo(file));
    }
}
