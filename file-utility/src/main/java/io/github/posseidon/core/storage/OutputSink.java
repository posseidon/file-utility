package io.github.posseidon.core.storage;

import io.github.posseidon.core.reference.FileReference;

import java.io.InputStream;

/** Writes content to a storage backend. */
public interface OutputSink {

    /** Whether this sink can handle the given target reference. */
    boolean supports(FileReference reference);

    /**
     * Write the supplied content to {@code target}. The stream is consumed and
     * closed by the implementation.
     *
     * @return metadata describing what was written
     */
    StoredObject write(FileReference target, InputStream content);

    /**
     * Whether writes to this sink are latency-bound (network, cloud object store, remote FS).
     * When {@code true}, callers may dedicate a separate I/O thread pool so that blocking writes
     * do not stall CPU-bound workers. Local filesystem sinks should return {@code false}.
     */
    default boolean isRemote() {
        return false;
    }
}
