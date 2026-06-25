package io.github.posseidon.pdf.chunk.model;

/** A named page-range specification for one logical chunk of a PDF. */
public record ChunkSpec(String name, String pageRanges) {
}
