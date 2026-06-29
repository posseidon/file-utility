package io.github.posseidon.core.ingest.processor;

import io.github.posseidon.core.ingest.MetadataExtractor;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Thread-safe: holds only final references to a thread-safe {@link MetadataExtractor}
 * and a caller-supplied {@link MetadataConsumer}. The consumer's thread safety is the
 * caller's responsibility.
 */
public final class MetadataExtractionProcessor implements FileProcessor {

    private final MetadataExtractor extractor;
    private final MetadataConsumer consumer;

    public MetadataExtractionProcessor(MetadataExtractor extractor, MetadataConsumer consumer) {
        this.extractor = extractor;
        this.consumer = consumer;
    }

    @Override
    public void process(Path path) throws IOException, InterruptedException {
        consumer.accept(extractor.extract(path));
    }
}
