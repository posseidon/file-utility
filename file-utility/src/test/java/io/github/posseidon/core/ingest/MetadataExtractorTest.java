package io.github.posseidon.core.ingest;

import io.github.posseidon.core.detect.ContentTypeDetector;
import io.github.posseidon.core.hash.Sha256Hash;
import io.github.posseidon.core.model.FileMetadata;
import io.github.posseidon.core.model.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataExtractorTest {

    // Magic bytes sufficient for Tika content-type detection (no real parsing needed).
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final byte[] PDF_MAGIC = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private final MetadataExtractor extractor =
            new MetadataExtractor(new Sha256Hash(), new ContentTypeDetector());

    // -------------------------------------------------------------------------
    // Happy path — all fields populated
    // -------------------------------------------------------------------------

    @Test
    void extractsAllFieldsForPlainTextFile(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("notes.txt");
        Files.writeString(file, "hello, world");
        Instant before = Instant.now();

        FileMetadata meta = extractor.extract(file);

        assertThat(meta.path()).isEqualTo(file);
        assertThat(meta.name()).isEqualTo("notes.txt");
        assertThat(meta.extension()).isEqualTo("txt");
        assertThat(meta.size()).isEqualTo(Files.size(file));
        assertThat(meta.mimeType()).isEqualTo("text/plain");
        assertThat(meta.mediaType()).isEqualTo(MediaType.TEXT);
        assertThat(meta.sha256()).hasSize(64);
        assertThat(meta.origin()).isNotBlank();
        assertThat(meta.owner()).isNotBlank();
        assertThat(meta.createdAt()).isNotNull().isBefore(before.plusSeconds(5));
        assertThat(meta.modifiedAt()).isNotNull().isBefore(before.plusSeconds(5));
        assertThat(meta.extra()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // SHA-256
    // -------------------------------------------------------------------------

    @Test
    void sha256MatchesKnownDigest(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("hello.txt");
        Files.write(file, "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(extractor.extract(file).sha256())
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void sha256OfEmptyFileIsKnownConstant(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("empty.txt");
        Files.write(file, new byte[0]);

        assertThat(extractor.extract(file).sha256())
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    // -------------------------------------------------------------------------
    // Extension
    // -------------------------------------------------------------------------

    @Test
    void extensionIsExtractedAndLowercased(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("report.PDF");
        Files.write(file, PDF_MAGIC);

        assertThat(extractor.extract(file).extension()).isEqualTo("pdf");
    }

    @Test
    void extensionIsEmptyWhenFilenameHasNoDot(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("README");
        Files.writeString(file, "no extension");

        assertThat(extractor.extract(file).extension()).isEmpty();
    }

    @Test
    void extensionIsEmptyWhenFilenameEndsWithDot(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("weird.");
        Files.writeString(file, "trailing dot");

        assertThat(extractor.extract(file).extension()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // MediaType mapping
    // -------------------------------------------------------------------------

    @Test
    void textFileMapsToTextMediaType(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("plain.txt");
        Files.writeString(file, "some text");

        assertThat(extractor.extract(file).mediaType()).isEqualTo(MediaType.TEXT);
    }

    @Test
    void pdfFileMapsToDocumentMediaType(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("doc.pdf");
        Files.write(file, PDF_MAGIC);

        assertThat(extractor.extract(file).mediaType()).isEqualTo(MediaType.DOCUMENT);
    }

    @Test
    void pngFileMapsToImageMediaType(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("photo.png");
        Files.write(file, PNG_MAGIC);

        assertThat(extractor.extract(file).mediaType()).isEqualTo(MediaType.IMAGE);
    }

    @Test
    void javaSourceFileMapsToCodeMediaType(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("Main.java");
        Files.writeString(file, "public class Main {}");

        // Tika detects .java as text/x-java-source → MediaType.CODE
        assertThat(extractor.extract(file).mediaType()).isEqualTo(MediaType.CODE);
    }

    // -------------------------------------------------------------------------
    // Size
    // -------------------------------------------------------------------------

    @Test
    void sizeMatchesActualByteCount(@TempDir Path tmp) throws IOException, InterruptedException {
        byte[] content = "exactly-12b".getBytes(StandardCharsets.UTF_8);
        Path file = tmp.resolve("sized.txt");
        Files.write(file, content);

        assertThat(extractor.extract(file).size()).isEqualTo(content.length);
    }

    @Test
    void sizeIsZeroForEmptyFile(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("empty.txt");
        Files.write(file, new byte[0]);

        assertThat(extractor.extract(file).size()).isZero();
    }

    // -------------------------------------------------------------------------
    // Extra metadata map
    // -------------------------------------------------------------------------

    @Test
    void extraMapFiltersOutContentTypeAndResourceName(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("notes.txt");
        Files.writeString(file, "test content");

        assertThat(extractor.extract(file).extra().keySet())
                .noneMatch(k -> k.equalsIgnoreCase("Content-Type") || k.equalsIgnoreCase("resourceName"));
    }

    @Test
    void extraMapIsImmutable(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("notes.txt");
        Files.writeString(file, "test content");

        assertThat(extractor.extract(file).extra()).isUnmodifiable();
    }

    @Test
    void extraMapValuesAreNonBlank(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("notes.txt");
        Files.writeString(file, "test content");

        assertThat(extractor.extract(file).extra().values())
                .allSatisfy(v -> assertThat(v).isNotBlank());
    }

    // -------------------------------------------------------------------------
    // Document type
    // -------------------------------------------------------------------------

    @Test
    void insightsIsNullWhenNoAnalyzerConfigured(@TempDir Path tmp) throws IOException, InterruptedException {
        Path file = tmp.resolve("notes.txt");
        Files.writeString(file, "hello, world");

        assertThat(extractor.extract(file).insights()).isNull();
    }

    // -------------------------------------------------------------------------
    // Thread safety
    // -------------------------------------------------------------------------

    @Test
    void concurrentExtractionsOnSameFileProduceConsistentResults(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("shared.txt"), "concurrent content");
        int threadCount = 20;
        List<FileMetadata> results = new CopyOnWriteArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.invokeAll(Collections.nCopies(threadCount, () -> {
                results.add(extractor.extract(file));
                return null;
            }));
        }

        assertThat(results)
                .hasSize(threadCount)
                .allSatisfy(m -> {
                    assertThat(m.path()).isEqualTo(file);
                    assertThat(m.sha256()).hasSize(64);
                });
    }
}
