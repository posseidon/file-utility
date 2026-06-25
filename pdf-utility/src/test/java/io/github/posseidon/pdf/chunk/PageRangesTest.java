package io.github.posseidon.pdf.chunk;

import io.github.posseidon.pdf.chunk.model.PageRange;
import io.github.posseidon.pdf.chunk.model.PageRanges;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRangesTest {

    @Test
    void parsesMultipleRanges() {
        List<PageRange> ranges = PageRanges.parse("1-3,4-10,11-", 12);
        assertEquals(3, ranges.size());
        assertEquals(new PageRange(1, 3), ranges.get(0));
        assertEquals(new PageRange(4, 10), ranges.get(1));
        assertEquals(new PageRange(11, 12), ranges.get(2)); // open-ended runs to total
    }

    @Test
    void parsesSinglePageToken() {
        assertEquals(List.of(new PageRange(5, 5)), PageRanges.parse("5", 10));
    }

    @Test
    void clampsEndToTotal() {
        assertEquals(List.of(new PageRange(8, 10)), PageRanges.parse("8-99", 10));
    }

    @Test
    void rejectsStartBeyondTotal() {
        assertThrows(IllegalArgumentException.class, () -> PageRanges.parse("20-25", 10));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> PageRanges.parse("  ", 10));
    }
}
