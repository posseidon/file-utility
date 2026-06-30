package io.github.posseidon.pdf.chunk.model;

import java.nio.file.Path;
import java.util.List;

/**
 * A PDF source paired with the named chunks to extract from it and where to write them.
 */
public record ChunkFixture(String path, String outputDir, List<ChunkSpec> chunks) {

    /**
     * Returns the configured output directory, or a {@code chunks/} sibling of the source if unset.
     */
    public Path resolvedOutputDir() {
        return outputDir != null ? Path.of(outputDir) : resolvedPath().getParent().resolve("chunks");
    }

    public Path resolvedPath() {
        return Path.of(path);
    }

    public ChunkFixture withOutputDir(Path dir) {
        return new ChunkFixture(path, dir.toString(), chunks);
    }

    @Override
    public String toString() {
        return resolvedPath().getFileName() + " — " + chunks.size() + " chapters";
    }
}
