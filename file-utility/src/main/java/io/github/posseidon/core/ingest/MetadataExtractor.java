package io.github.posseidon.core.ingest;

import io.github.posseidon.core.detect.ContentTypeDetector;
import io.github.posseidon.core.hash.Hasher;
import io.github.posseidon.core.model.FileMetadata;
import io.github.posseidon.core.model.MediaType;
import io.github.posseidon.core.util.FileUtility;
import io.github.posseidon.core.util.HostnameService;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles a {@link FileMetadata} for a single file.
 * Extraction steps (sequential here; L4 will fan them out in parallel):
 * 1. FS attributes — path, size, timestamps (single syscall)
 * 2. Owner         — FileOwnerAttributeView
 * 3. MIME          — Tika magic-byte sniff via {@link io.github.posseidon.core.detect.ContentTypeDetector}
 * 4. SHA-256       — full file read via {@link Hasher}
 * 5. Rich metadata — Tika AutoDetectParser (EXIF, video container, etc.) → extra map
 * Step 5 uses AutoDetectParser from tika-core; actual format parsers (EXIF, MP4, etc.)
 * are loaded via ServiceLoader — present at runtime in watcher-lab, injected in Spring.
 * If no parser is available for a format, extra is empty (best-effort, never throws).
 */
public final class MetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(MetadataExtractor.class);
    // AutoDetectParser is thread-safe; a single shared instance is fine.
    private static final AutoDetectParser PARSER = new AutoDetectParser();

    private final Hasher hasher;
    private final ContentTypeDetector mimeDetector;
    private final HostnameService hostnameProvider = new HostnameService();

    public MetadataExtractor(Hasher hasher, ContentTypeDetector mimeDetector) {
        this.hasher = hasher;
        this.mimeDetector = mimeDetector;
    }

    public FileMetadata extract(Path path) throws IOException, InterruptedException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        String hostName = hostnameProvider.getMachineName();
        String owner = FileUtility.readOwner(path);
        String mime = mimeDetector.detect(path);
        MediaType mediaType = MediaType.fromMime(mime);
        String sha256 = hasher.hash(path);
        List<String> directories = FileUtility.extractDirectories(path);
        Map<String, String> extra = extractExtra(path);

        return new FileMetadata(
                hostName,
                path,
                path.getFileName().toString(),
                FileUtility.extension(path),
                attrs.size(),
                attrs.creationTime().toInstant(),
                attrs.lastModifiedTime().toInstant(),
                owner,
                mime,
                mediaType,
                sha256,
                directories,
                extra
        );
    }

    private Map<String, String> extractExtra(Path path) {
        Metadata tikaMetadata = new Metadata();
        try (TikaInputStream in = TikaInputStream.get(path)) {
            // DefaultHandler discards text content — we only want the metadata side-effect.
            PARSER.parse(in, new DefaultHandler(), tikaMetadata, new ParseContext());
        } catch (Exception e) {
            log.debug("Tika could not fully parse {}: {}", path.getFileName(), e.getMessage());
        }

        Map<String, String> extra = new LinkedHashMap<>();
        for (String name : tikaMetadata.names()) {
            // Skip fields already captured in the primary record to avoid redundancy.
            if (name.equalsIgnoreCase("Content-Type") || name.equalsIgnoreCase("resourceName")) continue;
            String value = tikaMetadata.get(name);
            if (value != null && !value.isBlank()) {
                extra.put(name, value);
            }
        }
        return Map.copyOf(extra);
    }


}

