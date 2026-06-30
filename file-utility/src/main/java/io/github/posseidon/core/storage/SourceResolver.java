package io.github.posseidon.core.storage;

import io.github.posseidon.core.reference.FileReference;

import java.io.InputStream;

/**
 * Opens content for reading from a storage backend.
 */
public interface SourceResolver {

    /**
     * Whether this resolver can handle the given reference.
     */
    boolean supports(FileReference reference);

    /**
     * Open a fresh stream over the referenced content. Caller closes it.
     */
    InputStream openStream(FileReference reference);

    /**
     * Size of the referenced content in bytes.
     */
    long size(FileReference reference);
}
