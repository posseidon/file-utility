package io.github.posseidon.core.ingest;

import io.github.posseidon.core.ingest.processor.FileProcessor;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

public class DirectoryWatcher implements AutoCloseable {
    private final WatchService watcher;
    private final FileProcessor processor;
    private volatile boolean running = true;

    public DirectoryWatcher(FileProcessor first, FileProcessor... rest) throws IOException {
        FileProcessor chain = Objects.requireNonNull(first);
        for (FileProcessor next : rest) {
            chain = chain.andThen(Objects.requireNonNull(next));
        }
        this.processor = chain;
        this.watcher = FileSystems.getDefault().newWatchService();
    }

    public void startWatching(Path directory) throws IOException {
        // YAGNI: Flat directory watch for POC.
        directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);

        // Spawn an unpooled virtual thread specifically for the blocking take() loop
        Thread.ofVirtual().name("dir-watcher-loop").start(() -> {
            try {
                while (running) {
                    WatchKey key = watcher.take(); // Blocks efficiently without pinning an OS thread

                    List<Callable<Void>> tasks;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                        Path child = directory.resolve((Path) event.context());

                        if (Files.isRegularFile(child)) {
                            // Offload processing immediately to prevent blocking the listener
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
