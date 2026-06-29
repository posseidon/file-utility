package io.github.posseidon.core.ingest;

import io.github.posseidon.core.ingest.processor.FileProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class FileDiscoveryOrchestrator {

    private final FileProcessor processor;

    /**
     * Accepts one or more processors; multiple processors are composed left-to-right via
     * {@link FileProcessor#andThen}, making the chain of responsibility explicit at the call site:
     *
     * <pre>{@code
     * new FileDiscoveryOrchestrator(
     *     new MetadataExtractionProcessor(extractor, restPublisher.andThen(auditLogger)),
     *     new ArchiveProcessor(archivePath)
     * );
     * }</pre>
     */
    public FileDiscoveryOrchestrator(FileProcessor first, FileProcessor... rest) {
        FileProcessor chain = Objects.requireNonNull(first);
        for (FileProcessor next : rest) {
            chain = chain.andThen(Objects.requireNonNull(next));
        }
        this.processor = chain;
    }

    public void discoverAndProcess(Path root) throws IOException, InterruptedException {
        List<Callable<Void>> tasks;
        try (Stream<Path> paths = Files.walk(root)) {
            tasks = paths.filter(Files::isRegularFile).map(processor::bind).toList();
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Void> f : executor.invokeAll(tasks)) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    rethrow(e);
                }
            }
        }
    }

    private static void rethrow(ExecutionException e) throws IOException {
        Throwable cause = e.getCause();
        if (cause instanceof IOException io) throw io;
        if (cause instanceof RuntimeException re) throw re;
        throw new RuntimeException(cause);
    }
}
