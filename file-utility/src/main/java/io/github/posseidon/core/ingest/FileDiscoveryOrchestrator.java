package io.github.posseidon.core.ingest;

import io.github.posseidon.core.ingest.processor.FileProcessor;
import io.github.posseidon.core.util.FileUtility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class FileDiscoveryOrchestrator {

    private final FileProcessor processor;
    private final Predicate<Path> fileFilter;

    /**
     * Discovers and processes all regular, non-hidden files under a root directory.
     * Accepts one or more processors composed left-to-right via {@link FileProcessor#andThen}.
     */
    public FileDiscoveryOrchestrator(FileProcessor first, FileProcessor... rest) {
        this(p -> !FileUtility.isHidden(p), first, rest);
    }

    private FileDiscoveryOrchestrator(Predicate<Path> fileFilter, FileProcessor first, FileProcessor... rest) {
        this.fileFilter = Objects.requireNonNull(fileFilter);
        FileProcessor chain = Objects.requireNonNull(first);
        for (FileProcessor next : rest) {
            chain = chain.andThen(Objects.requireNonNull(next));
        }
        this.processor = chain;
    }

    /**
     * Like {@link #FileDiscoveryOrchestrator(FileProcessor, FileProcessor...)} but applies
     * {@code fileFilter} to each candidate path before binding it to a processor.
     * Use {@link FileUtility#processableFileFilter} to get a filter that also excludes
     * source-code files via Tika MIME detection.
     */
    public static FileDiscoveryOrchestrator withFilter(
            Predicate<Path> fileFilter, FileProcessor first, FileProcessor... rest) {
        return new FileDiscoveryOrchestrator(fileFilter, first, rest);
    }

    public void discoverAndProcess(Path root) throws IOException, InterruptedException {
        List<Callable<Void>> tasks;
        try (Stream<Path> paths = Files.walk(root)) {
            tasks = paths
                    .filter(Files::isRegularFile)
                    .filter(fileFilter)
                    .map(processor::bind)
                    .toList();
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
