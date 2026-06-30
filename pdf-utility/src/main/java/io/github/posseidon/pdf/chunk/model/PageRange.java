package io.github.posseidon.pdf.chunk.model;

/**
 * A 1-based, inclusive range of pages.
 */
public record PageRange(int start, int end) {

    public PageRange {
        if (start < 1) {
            throw new IllegalArgumentException("start must be >= 1, was " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end (" + end + ") must be >= start (" + start + ")");
        }
    }

    /**
     * Number of pages covered by this range.
     */
    public int count() {
        return end - start + 1;
    }
}
