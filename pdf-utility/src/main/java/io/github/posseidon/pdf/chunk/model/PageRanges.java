package io.github.posseidon.pdf.chunk.model;

import java.util.ArrayList;
import java.util.List;

/** Parses textual page-range specifications into {@link PageRange} values. */
public final class PageRanges {

    private PageRanges() {
    }

    /**
     * Parse a comma-separated spec such as {@code "1-3,4-10,11-"} or {@code "5,7,9"}.
     * Pages are 1-based and inclusive. A bare token {@code "5"} means {@code 5-5};
     * an open-ended token {@code "11-"} runs to {@code totalPages}; an end past the
     * document is clamped to {@code totalPages}; a start past the document is rejected.
     */
    public static List<PageRange> parse(String spec, int totalPages) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("Page range spec must not be empty");
        }
        if (totalPages < 1) {
            throw new IllegalArgumentException("totalPages must be >= 1, was " + totalPages);
        }

        List<PageRange> ranges = new ArrayList<>();
        for (String raw : spec.split(",")) {
            String token = raw.trim();
            if (!token.isEmpty()) {
                ranges.add(parseToken(token, totalPages));
            }
        }
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("No page ranges found in: " + spec);
        }
        return ranges;
    }

    private static PageRange parseToken(String token, int totalPages) {
        int dash = token.indexOf('-');
        try {
            if (dash < 0) {
                int page = Integer.parseInt(token);
                return bounded(page, page, totalPages);
            }
            String left = token.substring(0, dash).trim();
            String right = token.substring(dash + 1).trim();
            int start = left.isEmpty() ? 1 : Integer.parseInt(left);
            int end = right.isEmpty() ? totalPages : Integer.parseInt(right);
            return bounded(start, end, totalPages);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid page range token: '" + token + "'", e);
        }
    }

    private static PageRange bounded(int start, int end, int totalPages) {
        if (start > totalPages) {
            throw new IllegalArgumentException(
                    "Range start " + start + " exceeds document page count " + totalPages);
        }
        return new PageRange(start, Math.min(end, totalPages));
    }
}
