package io.github.posseidon.core.reference;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A {@link FileReference} backed by a path on the local filesystem.
 */
public record LocalFileReference(Path path) implements FileReference {

    public static final String SCHEME = "file";

    public LocalFileReference(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    public static LocalFileReference of(String pathString) {
        return new LocalFileReference(Path.of(pathString));
    }

    @Override
    public String scheme() {
        return SCHEME;
    }

    @Override
    public String location() {
        return path.toString();
    }

    @Override
    public String name() {
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    @Override
    public String toString() {
        return SCHEME + "://" + path;
    }
}
