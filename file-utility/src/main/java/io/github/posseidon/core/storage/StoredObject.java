package io.github.posseidon.core.storage;

import io.github.posseidon.core.reference.FileReference;

/**
 * Metadata describing a piece of content that has been written to a backend.
 */
public record StoredObject(String name, long sizeBytes, FileReference reference) {
}
