package io.github.posseidon.pdf.chunk;

import io.github.posseidon.core.storage.OutputSink;
import io.github.posseidon.core.storage.StoredObject;
import io.github.posseidon.pdf.chunk.model.*;
import io.github.posseidon.pdf.chunk.naming.LocalChunkNaming;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-level entry point: splits one or more PDFs by the specs in their {@link ChunkFixture}s
 * and persists each chunk through the supplied {@link OutputSink}.
 */
public final class PdfChunkService {

    private static final System.Logger LOGGER = System.getLogger(PdfChunkService.class.getName());

    private record IndexedRange(int index, PageRange range, LocalChunkNaming naming) {}

    private final OutputSink outputSink;

    public PdfChunkService(OutputSink outputSink) {
        this.outputSink = Objects.requireNonNull(outputSink, "outputSink");
    }

    /**
     * Processes every {@link ChunkFixture} in {@code fixtures}: splits each source PDF by its
     * {@link ChunkSpec}s and writes the resulting chunks to {@link ChunkFixture#resolvedOutputDir()}.
     * <p>
     * When {@link OutputSink#isRemote()} is {@code true}, split (CPU) and write (I/O) run on
     * separate pools so that blocking writes do not stall CPU workers. For local sinks a single
     * fixed pool is used — the handoff overhead of a second pool exceeds the latency of a local
     * disk write.
     *
     * @return flat manifest of every chunk written, in fixture/spec/range order
     */
    public List<PdfChunk> chunk(Collection<ChunkFixture> fixtures) {
        int nCpu = Runtime.getRuntime().availableProcessors();
        try (ExecutorService cpuPool = Executors.newFixedThreadPool(nCpu)) {
            if (outputSink.isRemote()) {
                try (ExecutorService ioPool = Executors.newVirtualThreadPerTaskExecutor()) {
                    return execute(fixtures, cpuPool, ioPool);
                }
            }
            return execute(fixtures, cpuPool, Runnable::run);
        }
    }

    /**
     * Phase 2 — collects results.
     * Submits all range tasks for every fixture first, then joins everything in one pass.
     * All N×M tasks (N fixtures × M ranges each) run in parallel across the pool before
     * any result is awaited, avoiding the per-fixture sequential bottleneck.
     */
    private List<PdfChunk> execute(Collection<ChunkFixture> fixtures,
                                   ExecutorService cpuPool, Executor ioPool) {
        AtomicInteger pdfChunkCounter = new AtomicInteger();
        List<CompletableFuture<PdfChunk>> allFutures = fixtures.stream()
                .flatMap(fixture -> submit(fixture, cpuPool, ioPool).stream())
                .map(f -> f.thenApply(chunk -> { pdfChunkCounter.incrementAndGet(); return chunk; }))
                .toList();
        List<PdfChunk> results = allFutures.stream().map(CompletableFuture::join).toList();
        LOGGER.log(System.Logger.Level.INFO, "Processed {0} PDF chunks", pdfChunkCounter.get());
        return results;
    }

    /** Phase 1 — reads the fixture, builds all range tasks, and enqueues one future per range. */
    private List<CompletableFuture<PdfChunk>> submit(ChunkFixture fixture,
                                                      ExecutorService cpuPool, Executor ioPool) {
        try {
            byte[] pdfBytes = Files.readAllBytes(fixture.resolvedPath());
            List<IndexedRange> tasks = buildTasks(fixture, pdfBytes);
            ThreadLocal<PDDocument> threadDoc = threadLocalDoc(pdfBytes);
            return tasks.stream()
                    .map(task -> toFuture(task, threadDoc, cpuPool, ioPool))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Flattens all {@link ChunkSpec}s in the fixture into a flat, indexed range list.
     * Pure function — no I/O side effects beyond the single page-count load.
     */
    private static List<IndexedRange> buildTasks(ChunkFixture fixture, byte[] pdfBytes) throws IOException {
        Path outputDir = fixture.resolvedOutputDir();
        int total;
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            total = doc.getNumberOfPages();
        }
        List<IndexedRange> tasks = new ArrayList<>();
        int index = 0;
        for (ChunkSpec spec : fixture.chunks()) {
            LocalChunkNaming naming = new LocalChunkNaming(outputDir, spec.name());
            for (PageRange range : PageRanges.parse(spec.pageRanges(), total)) {
                tasks.add(new IndexedRange(index++, range, naming));
            }
        }
        return tasks;
    }

    /**
     * One {@link PDDocument} per platform thread, loaded lazily from {@code pdfBytes}.
     * Each fixture call creates its own {@link ThreadLocal} instance, so tasks from
     * different fixtures on the same thread each hold their own document.
     */
    private static ThreadLocal<PDDocument> threadLocalDoc(byte[] pdfBytes) {
        return ThreadLocal.withInitial(() -> {
            try {
                return Loader.loadPDF(pdfBytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Builds the split → write pipeline for one range task. */
    private CompletableFuture<PdfChunk> toFuture(IndexedRange task, ThreadLocal<PDDocument> threadDoc,
                                                   ExecutorService cpuPool, Executor ioPool) {
        CompletableFuture<PageContent> split = CompletableFuture.supplyAsync(() -> {
            try {
                return new PdfSplitTask(threadDoc.get(), task.range()).call();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, cpuPool);

        return split.thenApplyAsync(content -> writeChunk(task, content), ioPool);
    }

    /** Persists one split chunk through the {@link OutputSink} and wraps the result. */
    private PdfChunk writeChunk(IndexedRange task, PageContent content) {
        StoredObject stored = outputSink.write(
                task.naming().targetFor(task.index(), content.range()),
                new ByteArrayInputStream(content.content()));
        return new PdfChunk(content.range(), stored);
    }
}
