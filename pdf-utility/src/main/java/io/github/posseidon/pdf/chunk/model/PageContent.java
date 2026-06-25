package io.github.posseidon.pdf.chunk.model;

/** A produced PDF chunk held in memory: the page range it covers and its bytes. */
public record PageContent(PageRange range, byte[] content) {
}
