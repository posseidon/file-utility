package io.github.posseidon.pdf.chunk.naming;

import io.github.posseidon.core.reference.FileReference;
import io.github.posseidon.core.reference.LocalFileReference;
import io.github.posseidon.pdf.chunk.model.PageRange;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Names chunks {@code <baseName>_<NNN>_p<start>-<end>.pdf} inside a directory.
 */
public final class LocalChunkNaming implements ChunkNaming {

    private final Path directory;
    private final String baseName;

    public LocalChunkNaming(Path directory, String baseName) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.baseName = Objects.requireNonNull(baseName, "baseName");
    }

    @Override
    public FileReference targetFor(int index, PageRange range) {
        String fileName = "%s_%03d_p%d-%d.pdf".formatted(baseName, index + 1, range.start(), range.end());
        return new LocalFileReference(directory.resolve(fileName));
    }
}
