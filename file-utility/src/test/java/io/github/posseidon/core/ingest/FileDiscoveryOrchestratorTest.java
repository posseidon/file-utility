package io.github.posseidon.core.ingest;

import io.github.posseidon.core.ingest.processor.FileProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileDiscoveryOrchestratorTest {

    // -------------------------------------------------------------------------
    // Discovery — what gets processed
    // -------------------------------------------------------------------------

    @Test
    void processesAllFilesInFlatDirectory(@TempDir Path tmp) throws IOException, InterruptedException {
        Files.writeString(tmp.resolve("a.txt"), "a");
        Files.writeString(tmp.resolve("b.txt"), "b");
        Files.writeString(tmp.resolve("c.txt"), "c");
        List<Path> processed = new CopyOnWriteArrayList<>();

        new FileDiscoveryOrchestrator(processed::add).discoverAndProcess(tmp);

        assertThat(processed).containsExactlyInAnyOrder(
                tmp.resolve("a.txt"), tmp.resolve("b.txt"), tmp.resolve("c.txt"));
    }

    @Test
    void recursivelyProcessesNestedDirectories(@TempDir Path tmp) throws IOException, InterruptedException {
        Files.writeString(tmp.resolve("root.txt"), "r");
        Path sub = Files.createDirectory(tmp.resolve("sub"));
        Files.writeString(sub.resolve("nested.txt"), "n");
        Path deep = Files.createDirectory(sub.resolve("deep"));
        Files.writeString(deep.resolve("deepest.txt"), "d");
        List<Path> processed = new CopyOnWriteArrayList<>();

        new FileDiscoveryOrchestrator(processed::add).discoverAndProcess(tmp);

        assertThat(processed).hasSize(3)
                .containsExactlyInAnyOrder(
                        tmp.resolve("root.txt"),
                        sub.resolve("nested.txt"),
                        deep.resolve("deepest.txt"));
    }

    @Test
    void skipsDirectories(@TempDir Path tmp) throws IOException, InterruptedException {
        Files.createDirectory(tmp.resolve("subdir"));
        Files.writeString(tmp.resolve("file.txt"), "x");
        List<Path> processed = new CopyOnWriteArrayList<>();

        new FileDiscoveryOrchestrator(processed::add).discoverAndProcess(tmp);

        assertThat(processed).containsExactly(tmp.resolve("file.txt"));
    }

    @Test
    void emptyDirectoryResultsInZeroCalls(@TempDir Path tmp) throws IOException, InterruptedException {
        AtomicInteger count = new AtomicInteger();

        new FileDiscoveryOrchestrator(path -> count.incrementAndGet()).discoverAndProcess(tmp);

        assertThat(count).hasValue(0);
    }

    // -------------------------------------------------------------------------
    // Chain of responsibility
    // -------------------------------------------------------------------------

    @Test
    void allProcessorsInChainAreCalledForEveryFile(@TempDir Path tmp) throws IOException, InterruptedException {
        Files.writeString(tmp.resolve("a.txt"), "a");
        Files.writeString(tmp.resolve("b.txt"), "b");
        AtomicInteger firstCount = new AtomicInteger();
        AtomicInteger secondCount = new AtomicInteger();

        new FileDiscoveryOrchestrator(
                path -> firstCount.incrementAndGet(),
                path -> secondCount.incrementAndGet()
        ).discoverAndProcess(tmp);

        assertThat(firstCount).hasValue(2);
        assertThat(secondCount).hasValue(2);
    }

    @Test
    void chainedProcessorsAreCalledInDeclarationOrder(@TempDir Path tmp) throws IOException, InterruptedException {
        Files.writeString(tmp.resolve("only.txt"), "x");
        List<String> order = new CopyOnWriteArrayList<>();

        new FileDiscoveryOrchestrator(
                path -> order.add("first"),
                path -> order.add("second"),
                path -> order.add("third")
        ).discoverAndProcess(tmp);

        assertThat(order).containsExactly("first", "second", "third");
    }

    @Test
    void secondProcessorIsNotCalledWhenFirstThrows(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("file.txt"), "x");
        AtomicInteger secondCallCount = new AtomicInteger();

        assertThatThrownBy(() ->
                new FileDiscoveryOrchestrator(
                        path -> {throw new IOException("first fails");},
                        path -> secondCallCount.incrementAndGet()
                ).discoverAndProcess(tmp))
                .isInstanceOf(IOException.class);

        assertThat(secondCallCount).hasValue(0);
    }

    // -------------------------------------------------------------------------
    // Exception propagation
    // -------------------------------------------------------------------------

    @Test
    void ioExceptionFromProcessorPropagates(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("file.txt"), "x");

        assertThatThrownBy(() ->
                new FileDiscoveryOrchestrator(
                        path -> {throw new IOException("processor failure");}
                ).discoverAndProcess(tmp))
                .isInstanceOf(IOException.class)
                .hasMessage("processor failure");
    }

    @Test
    void runtimeExceptionFromProcessorPropagates(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("file.txt"), "x");

        assertThatThrownBy(() ->
                new FileDiscoveryOrchestrator(
                        path -> {throw new IllegalStateException("bad state");}
                ).discoverAndProcess(tmp))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bad state");
    }

    // -------------------------------------------------------------------------
    // Concurrency
    // -------------------------------------------------------------------------

    @Test
    void filesAreProcessedOnVirtualThreads(@TempDir Path tmp) throws IOException, InterruptedException {
        for (int i = 0; i < 10; i++) {
            Files.writeString(tmp.resolve("f" + i + ".txt"), "x");
        }
        Set<Boolean> virtualFlags = ConcurrentHashMap.newKeySet();

        new FileDiscoveryOrchestrator(
                path -> virtualFlags.add(Thread.currentThread().isVirtual())
        ).discoverAndProcess(tmp);

        assertThat(virtualFlags).containsExactly(true);
    }

    @Test
    void allFilesAreProcessedConcurrently(@TempDir Path tmp) throws IOException, InterruptedException {
        int fileCount = 20;
        for (int i = 0; i < fileCount; i++) {
            Files.writeString(tmp.resolve("f" + i + ".txt"), "x");
        }
        List<Path> processed = new CopyOnWriteArrayList<>();

        new FileDiscoveryOrchestrator(processed::add).discoverAndProcess(tmp);

        assertThat(processed).hasSize(fileCount);
    }

    // -------------------------------------------------------------------------
    // FileProcessor.bind() integration
    // -------------------------------------------------------------------------

    @Test
    void bindProducesCallableThatInvokesProcess(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("file.txt"), "x");
        AtomicInteger count = new AtomicInteger();
        FileProcessor processor = path -> count.incrementAndGet();

        processor.bind(file).call();

        assertThat(count).hasValue(1);
    }

    @Test
    void bindCallableReturnsNull(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("file.txt"), "x");
        FileProcessor processor = path -> {};

        assertThat(processor.bind(file).call()).isNull();
    }
}
