package io.github.posseidon.pdf.chunk.helper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.posseidon.pdf.chunk.model.ChunkFixture;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

public final class ChunkFixtures {
    private ChunkFixtures() {}

    /**
     * One {@link ChunkFixture} per JSON entry — for whole-PDF parameterized tests.
     */
    public static Stream<Arguments> fixtureStream() throws IOException {
        return load().stream().map(Arguments::of);
    }

    /**
     * Loads all {@link ChunkFixture} entries from {@code chunks.json}.
     */
    public static List<ChunkFixture> load() throws IOException {
        try (InputStream in = ChunkFixtures.class
                .getClassLoader().getResourceAsStream("chunks.json")) {
            return new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
    }

    /**
     * Flattened: one {@code (chunkName, pdfPath, pageRanges)} per {@link ChunkSpec}.
     */
    public static Stream<Arguments> stream() throws IOException {
        return load().stream()
                .flatMap(f -> f.chunks().stream()
                        .map(c -> Arguments.of(c.name(), f.resolvedPath(), c.pageRanges())));
    }
}
