package io.github.posseidon.pdf.chunk.naming;

import io.github.posseidon.core.reference.FileReference;
import io.github.posseidon.pdf.chunk.model.PageRange;

/**
 * Strategy that produces the output target for each chunk.
 */
@FunctionalInterface
public interface ChunkNaming {

    /**
     * @param index zero-based position of the chunk in the request
     * @param range the page range the chunk covers
     * @return where the chunk should be written
     */
    FileReference targetFor(int index, PageRange range);
}
