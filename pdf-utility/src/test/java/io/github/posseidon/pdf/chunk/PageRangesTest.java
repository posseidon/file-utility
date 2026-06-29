package io.github.posseidon.pdf.chunk;

import io.github.posseidon.pdf.chunk.model.PageRange;
import io.github.posseidon.pdf.chunk.model.PageRanges;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRangesTest {

    @Test
    void parsesMultipleRanges() {
        List<PageRange> ranges = PageRanges.parse("1-3,4-10,11-", 12);
        assertThat(ranges).hasSize(3);
        assertThat(ranges.get(0)).isEqualTo(new PageRange(1, 3));
        assertThat(ranges.get(1)).isEqualTo(new PageRange(4, 10));
        assertThat(ranges.get(2)).isEqualTo(new PageRange(11, 12));
    }

    @Test
    void parsesSinglePageToken() {
        assertThat(PageRanges.parse("5", 10)).isEqualTo(List.of(new PageRange(5, 5)));
    }

    @Test
    void clampsEndToTotal() {
        assertThat(PageRanges.parse("8-99", 10)).isEqualTo(List.of(new PageRange(8, 10)));
    }

    @Test
    void rejectsStartBeyondTotal() {
        assertThatThrownBy(() -> PageRanges.parse("20-25", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> PageRanges.parse("  ", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
