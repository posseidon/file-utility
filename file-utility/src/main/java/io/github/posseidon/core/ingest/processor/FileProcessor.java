package io.github.posseidon.core.ingest.processor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public interface FileProcessor {
    void process(Path path) throws IOException, InterruptedException;

    /** Binds this processor to a specific path, producing a {@link Callable} the executor can submit directly. */
    default Callable<Void> bind(Path path) {
        return () -> { process(path); return null; };
    }

    default FileProcessor andThen(FileProcessor next) {
        return path -> {
            this.process(path);
            next.process(path);
        };
    }
}
