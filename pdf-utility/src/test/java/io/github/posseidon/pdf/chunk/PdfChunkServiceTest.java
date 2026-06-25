package io.github.posseidon.pdf.chunk;

import io.github.posseidon.core.storage.local.LocalFileSystemStorage;
import io.github.posseidon.pdf.chunk.helper.ChunkFixtures;
import io.github.posseidon.pdf.chunk.helper.TimingExtension;
import io.github.posseidon.pdf.chunk.model.ChunkFixture;
import io.github.posseidon.pdf.chunk.model.ChunkSpec;
import io.github.posseidon.pdf.chunk.model.PageContent;
import io.github.posseidon.pdf.chunk.model.PageRange;
import io.github.posseidon.pdf.chunk.model.PageRanges;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(TimingExtension.class)
class PdfChunkServiceTest {

    private static final int THREAD_COUNT = 8;
    private static final int TIMEOUT_SECONDS = 30;

    @Test
    @DisplayName("chunk Avatamsaka_Sutra.pdf into 2 ranges [sequential / baseline]")
    void chunksToLocalDirectory(@TempDir Path tmp) throws IOException, URISyntaxException {
        Path source = Path.of(Objects.requireNonNull(PdfChunkServiceTest.class
                        .getClassLoader()
                        .getResource("Avatamsaka_Sutra.pdf"))
                .toURI());

        Path outDir = tmp.resolve("chunks");
        ChunkFixture fixture = new ChunkFixture(
                source.toString(),
                outDir.toString(),
                List.of(new ChunkSpec("range-1-4", "1-4"), new ChunkSpec("range-5-10", "5-10")));

        PdfChunkService service = new PdfChunkService(new LocalFileSystemStorage());
        List<PdfChunk> manifest = service.chunk(List.of(fixture));

        assertEquals(2, manifest.size());
        assertEquals(4, manifest.get(0).pageCount());
        assertEquals(6, manifest.get(1).pageCount());
        for (PdfChunk chunk : manifest) {
            assertTrue(chunk.sizeBytes() > 0, "chunk should be non-empty");
        }
        try (Stream<Path> files = Files.list(outDir)) {
            assertEquals(2, files.count());
        }
    }

    // ---------------------------------------------------------------------------
    // Real-PDF tests driven by chunks.json
    // ---------------------------------------------------------------------------

    @ParameterizedTest(name = "[sequential / per-fixture] {0}")
    @MethodSource("io.github.posseidon.pdf.chunk.helper.ChunkFixtures#fixtureStream")
    void chunksAllChaptersToDirectory(ChunkFixture fixture, @TempDir Path tmp) throws IOException {
        PdfChunkService service = new PdfChunkService(new LocalFileSystemStorage());

        List<PdfChunk> manifest = service.chunk(List.of(fixture.withOutputDir(tmp)));

        assertEquals(fixture.chunks().size(), manifest.size(), "manifest size must match chapter count");
        for (int i = 0; i < manifest.size(); i++) {
            PdfChunk chunk = manifest.get(i);
            assertTrue(chunk.sizeBytes() > 0, "chunk is empty: " + fixture.chunks().get(i).name());
        }
        try (Stream<Path> files = Files.list(tmp)) {
            assertEquals(fixture.chunks().size(), files.count(), "one file per chapter expected");
        }
    }

    @Test
    @DisplayName("chunk all 41 chapters — sequential, one chapter at a time")
    void chunksAllFixturesSequentially(@TempDir Path tmp) throws IOException {
        List<ChunkFixture> fixtures = ChunkFixtures.load();
        PdfChunkService service = new PdfChunkService(new LocalFileSystemStorage());

        for (ChunkFixture fixture : fixtures) {
            Path outDir = Files.createDirectories(
                    tmp.resolve(fixture.resolvedPath().getFileName().toString()));

            List<PdfChunk> manifest = service.chunk(List.of(fixture.withOutputDir(outDir)));

            assertEquals(fixture.chunks().size(), manifest.size());
            for (int i = 0; i < manifest.size(); i++) {
                assertTrue(manifest.get(i).sizeBytes() > 0,
                        "empty chunk for: " + fixture.chunks().get(i).name());
            }
        }
    }

    @Test
    @DisplayName("chunk all 41 chapters — parallel, " + THREAD_COUNT + " threads via PdfSplitTask")
    void chunksAllFixturesParallel() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        List<ChunkFixture> fixtures = ChunkFixtures.load();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT)) {
            for (ChunkFixture fixture : fixtures) {
                byte[] pdfBytes = Files.readAllBytes(fixture.resolvedPath());
                int totalPages = pageCount(pdfBytes);
                List<PageRange> allRanges = fixture.chunks().stream()
                        .flatMap(spec -> PageRanges.parse(spec.pageRanges(), totalPages).stream())
                        .toList();

                // Each task opens its own PDDocument from the shared byte[].
                // pdfBytes is read-only so sharing it across threads is safe;
                // sharing a single PDDocument is not — PDPageTree is not thread-safe.
                CopyOnWriteArrayList<PageContent> results = new CopyOnWriteArrayList<>();
                List<CompletableFuture<Void>> futures = allRanges.stream()
                        .map(range -> CompletableFuture
                                .supplyAsync(() -> {
                                    try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                                        return new PdfSplitTask(doc, range).call();
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                }, pool)
                                .thenAccept(results::add))
                        .toList();
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                assertEquals(allRanges.size(), results.size());
                results.sort(Comparator.comparingInt(pc -> pc.range().start()));
                for (int i = 0; i < allRanges.size(); i++) {
                    assertEquals(allRanges.get(i).count(), pageCount(results.get(i).content()),
                            "page count mismatch for: " + fixture.chunks().get(i).name());
                }
            }
        }
    }

    @Test
    @DisplayName("chunk all chapters — write to ~/Downloads/Avatamsaka_Sutra")
    void chunksToDownloadsFolder() throws IOException {
        List<ChunkFixture> fixtures = ChunkFixtures.load();
        Path outDir = Path.of(System.getProperty("user.home"), "Downloads", "Avatamsaka_Sutra");
        Files.createDirectories(outDir);

        PdfChunkService service = new PdfChunkService(new LocalFileSystemStorage());
        List<PdfChunk> manifest = service.chunk(
                fixtures.stream().map(f -> f.withOutputDir(outDir)).toList());

        int expectedTotal = fixtures.stream().mapToInt(f -> f.chunks().size()).sum();
        assertEquals(expectedTotal, manifest.size());
        for (PdfChunk chunk : manifest) {
            assertTrue(chunk.sizeBytes() > 0);
        }
    }

    private static int pageCount(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }
}
