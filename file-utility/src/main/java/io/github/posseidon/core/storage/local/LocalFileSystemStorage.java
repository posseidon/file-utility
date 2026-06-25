package io.github.posseidon.core.storage.local;

import io.github.posseidon.core.FileUtilityException;
import io.github.posseidon.core.reference.FileReference;
import io.github.posseidon.core.reference.LocalFileReference;
import io.github.posseidon.core.storage.OutputSink;
import io.github.posseidon.core.storage.SourceResolver;
import io.github.posseidon.core.storage.StoredObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local-filesystem implementation of both {@link SourceResolver} and {@link OutputSink}. */
public final class LocalFileSystemStorage implements SourceResolver, OutputSink {

    @Override
    public boolean supports(FileReference reference) {
        return reference instanceof LocalFileReference;
    }

    @Override
    public InputStream openStream(FileReference reference) {
        Path path = asPath(reference);
        try {
            return new BufferedInputStream(Files.newInputStream(path));
        } catch (IOException e) {
            throw new FileUtilityException("Unable to open file: " + path, e);
        }
    }

    @Override
    public long size(FileReference reference) {
        Path path = asPath(reference);
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new FileUtilityException("Unable to determine size of: " + path, e);
        }
    }

    @Override
    public StoredObject write(FileReference target, InputStream content) {
        Path path = asPath(target);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            long bytes;
            try (InputStream in = content; OutputStream out = Files.newOutputStream(path)) {
                bytes = in.transferTo(out);
            }
            return new StoredObject(path.getFileName().toString(), bytes, new LocalFileReference(path));
        } catch (IOException e) {
            throw new FileUtilityException("Unable to write file: " + path, e);
        }
    }

    private static Path asPath(FileReference reference) {
        if (reference instanceof LocalFileReference local) {
            return local.path();
        }
        throw new FileUtilityException("Unsupported reference for local storage: " + reference);
    }
}
