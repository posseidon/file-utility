package io.github.posseidon.core.ingest.processor;

import io.github.posseidon.core.model.FileMetadata;

import java.io.IOException;

@FunctionalInterface
public interface MetadataConsumer {
    void accept(FileMetadata metadata) throws IOException, InterruptedException;

    default MetadataConsumer andThen(MetadataConsumer next) {
        return metadata -> {
            this.accept(metadata);
            next.accept(metadata);
        };
    }
}
