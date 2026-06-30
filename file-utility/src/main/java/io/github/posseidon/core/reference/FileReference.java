package io.github.posseidon.core.reference;

/**
 * A backend-agnostic pointer to a piece of content (a source to read or a
 * target to write). Implementations describe a specific backend, e.g. the
 * local filesystem or, in a later module, Azure Blob storage.
 */
public interface FileReference {

    /**
     * Scheme identifying the backend, e.g. {@code "file"} or {@code "blob"}.
     */
    String scheme();

    /**
     * Raw locator within the backend (a path, a blob URI, ...).
     */
    String location();

    /**
     * A short display/file name for this reference.
     */
    String name();
}
