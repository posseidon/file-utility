package io.github.posseidon.core.ingest;

import io.github.posseidon.core.ingest.processor.FileProcessor;
import io.github.posseidon.core.util.FileUtility;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.function.Predicate;

public class DirectoryWatcher implements AutoCloseable {

    private final WatchService watcher;
    private final FileProcessor processor;
    private final Predicate<Path> fileFilter;
    private volatile boolean running = true;

    /**
     * Watches a directory and processes all regular, non-hidden files that appear or change.
     */
    public DirectoryWatcher(FileProcessor first, FileProcessor... rest) throws IOException {
        this(p -> !FileUtility.isHidden(p), first, rest);
    }

    private DirectoryWatcher(Predicate<Path> fileFilter, FileProcessor first, FileProcessor... rest) throws IOException {
        this.fileFilter = Objects.requireNonNull(fileFilter);
        FileProcessor chain = Objects.requireNonNull(first);
        for (FileProcessor next : rest) {
            chain = chain.andThen(Objects.requireNonNull(next));
        }
        this.processor = chain;
        this.watcher = FileSystems.getDefault().newWatchService();
    }

    /**
     * Like {@link #DirectoryWatcher(FileProcessor, FileProcessor...)} but applies
     * {@code fileFilter} before handing a path to the processor chain.
     * Use {@link FileUtility#processableFileFilter} to also exclude source-code files.
     */
    public static DirectoryWatcher withFilter(
            Predicate<Path> fileFilter, FileProcessor first, FileProcessor... rest) throws IOException {
        return new DirectoryWatcher(fileFilter, first, rest);
    }

    public void startWatching(Path directory) throws IOException {
        // YAGNI: Flat directory watch for POC.
        directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

        Thread.ofVirtual().name("dir-watcher-loop").start(() -> {
            try {
                while (running) {
                    WatchKey key = watcher.take();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                        Path child = directory.resolve((Path) event.context());

                        if (Files.isRegularFile(child) && fileFilter.test(child)) {
                            Thread.ofVirtual().start(() -> processor.bind(child));
                        }
                    }
                    if (!key.reset()) break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Watcher interrupted");
            }
        });
    }

    @Override
    public void close() throws Exception {
        this.running = false;
        this.watcher.close();
    }
}
