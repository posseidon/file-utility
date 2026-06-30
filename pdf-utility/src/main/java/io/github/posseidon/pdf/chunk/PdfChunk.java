package io.github.posseidon.pdf.chunk;

import io.github.posseidon.core.storage.StoredObject;
import io.github.posseidon.pdf.chunk.model.PageRange;

/**
 * One entry in a chunking manifest: the page range and where it was stored.
 */
public record PdfChunk(PageRange range, StoredObject stored) {

    public String name() {
        return stored.name();
    }

    public long sizeBytes() {
        return stored.sizeBytes();
    }

    public int pageCount() {
        return range.count();
    }
}
