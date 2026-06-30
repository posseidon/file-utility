# file-utility

A small, framework-free Java 21 library for file work, plus a `pdf-utility`
module that splits PDFs into smaller PDFs by page range.

## Modules

| Module         | Artifact ID    | Purpose                                                                                                    | Key dependency          |
|----------------|----------------|------------------------------------------------------------------------------------------------------------|-------------------------|
| `file-utility` | `file-utility` | Content-type detection, SHA-256 hashing, metadata extraction, concurrent file ingest, storage abstractions | Apache **Tika** 3.3.1   |
| `pdf-utility`  | `pdf-utility`  | PDF chunker: split one PDF into smaller PDFs by 1-based page ranges                                        | Apache **PDFBox** 3.0.7 |

Group ID for both modules: `io.github.posseidon`

## Build

Requires **JDK 21** and **Maven 3.9+**.

```bash
mvn clean install
```

Compiles both modules, runs all tests, and installs the jars into `~/.m2`.

---

## file-utility

### Maven dependency

```xml

<dependency>
    <groupId>io.github.posseidon</groupId>
    <artifactId>file-utility</artifactId>
    <version>0.1.2-SNAPSHOT</version>
</dependency>
```

---

### Content-type detection

`ContentTypeDetector` is a thin, thread-safe wrapper around Apache Tika.

```java
import io.github.posseidon.core.detect.ContentTypeDetector;

var detector = new ContentTypeDetector();

// Detect from a file on disk (magic-byte sniff + extension hint)
String mime = detector.detect(Path.of("report.pdf"));   // "application/pdf"

// Detect from a stream with a filename hint
String mime2 = detector.detect(inputStream, "photo.png"); // "image/png"

// Convenience check
boolean isPdf = detector.isPdf(Path.of("report.pdf"));   // true
```

---

### Hashing

`Hasher` is a single-method interface; `Sha256Hash` is the built-in implementation.
Each call creates a fresh `MessageDigest` so the class is safe for concurrent use.

```java
import io.github.posseidon.core.hash.Hasher;
import io.github.posseidon.core.hash.Sha256Hash;

Hasher hasher = new Sha256Hash();
String hex = hasher.hash(Path.of("data.bin")); // 64-char lowercase hex string
```

Custom hasher:

```java
Hasher md5 = path -> {
    MessageDigest d = MessageDigest.getInstance("MD5");
    // ... read file, update digest ...
    return HexFormat.of().formatHex(d.digest());
};
```

---

### Metadata extraction

`MetadataExtractor` assembles a `FileMetadata` record for a single file in one
call. It requires a `Hasher` and a `ContentTypeDetector`; both are injectable so
you can swap algorithms or use mocks in tests.

```java
import io.github.posseidon.core.detect.ContentTypeDetector;
import io.github.posseidon.core.hash.Sha256Hash;
import io.github.posseidon.core.ingest.MetadataExtractor;
import io.github.posseidon.core.model.FileMetadata;

var extractor = new MetadataExtractor(new Sha256Hash(), new ContentTypeDetector());
FileMetadata meta = extractor.extract(Path.of("report.pdf"));
```

#### `FileMetadata` record fields

| Field        | Type                  | Description                                            |
|--------------|-----------------------|--------------------------------------------------------|
| `origin`     | `String`              | Hostname of the machine that extracted the metadata    |
| `path`       | `Path`                | Absolute path to the file                              |
| `name`       | `String`              | File name (last segment of the path)                   |
| `extension`  | `String`              | Lowercase extension without the dot, or `""` if absent |
| `size`       | `long`                | File size in bytes                                     |
| `createdAt`  | `Instant`             | File creation timestamp (from the OS)                  |
| `modifiedAt` | `Instant`             | Last-modified timestamp (from the OS)                  |
| `owner`      | `String`              | OS file owner name                                     |
| `mimeType`   | `String`              | MIME type detected by Tika (e.g. `"text/plain"`)       |
| `mediaType`  | `MediaType`           | Coarser category derived from the MIME type            |
| `sha256`     | `String`              | 64-char lowercase hex digest                           |
| `extra`      | `Map<String, String>` | Immutable map of additional Tika metadata (EXIF, etc.) |

#### `MediaType` enum

`TEXT`, `CODE`, `IMAGE`, `VIDEO`, `AUDIO`, `DOCUMENT`, `ARCHIVE`, `DATA`, `OTHER`

MIME-to-category mapping is handled by `MediaType.fromMime(String)`.

---

### Concurrent file discovery and processing

`FileDiscoveryOrchestrator` walks a directory tree recursively, submits each
regular file to a **virtual-thread-per-task executor**, and propagates exceptions
faithfully after all tasks complete.

```java
import io.github.posseidon.core.ingest.FileDiscoveryOrchestrator;
import io.github.posseidon.core.ingest.MetadataExtractor;
import io.github.posseidon.core.ingest.processor.MetadataExtractionProcessor;
import io.github.posseidon.core.detect.ContentTypeDetector;
import io.github.posseidon.core.hash.Sha256Hash;

var extractor = new MetadataExtractor(new Sha256Hash(), new ContentTypeDetector());
var processor = new MetadataExtractionProcessor(extractor, meta -> System.out.println(meta));

new

FileDiscoveryOrchestrator(processor).

discoverAndProcess(Path.of("/data/files"));
```

Multiple processors are chained left-to-right (Chain of Responsibility):

```java
new FileDiscoveryOrchestrator(
        validateProcessor,          // runs first for every file
        metadataExtractionProcessor // runs second for every file
).

discoverAndProcess(root);
```

---

### Chain of Responsibility

Two composable interfaces enable multi-step pipelines.

#### `FileProcessor` — file-level operations

```java
import io.github.posseidon.core.ingest.processor.FileProcessor;

FileProcessor a = path -> log("step A: " + path);
FileProcessor b = path -> log("step B: " + path);
FileProcessor chain = a.andThen(b); // both run for each file, in order
```

`FileProcessor.bind(Path)` converts a processor into a `Callable<Void>` for
direct submission to any `ExecutorService`:

```java
Callable<Void> task = processor.bind(Path.of("file.txt"));
executor.

submit(task);
```

#### `MetadataConsumer` — post-extraction operations

```java
import io.github.posseidon.core.ingest.processor.MetadataConsumer;

MetadataConsumer store = meta -> database.save(meta);
MetadataConsumer audit = meta -> auditLog.record(meta.sha256());
MetadataConsumer pipeline = store.andThen(audit);

var processor = new MetadataExtractionProcessor(extractor, pipeline);
```

---

### `MetadataExtractionProcessor`

Bridges `FileProcessor` and `MetadataConsumer`: extracts metadata from a path
then forwards it to the consumer. Thread-safe by construction (all fields are
`final`; extraction creates no shared mutable state).

```java
import io.github.posseidon.core.ingest.processor.MetadataExtractionProcessor;

MetadataConsumer consumer = meta -> publish(meta);
var processor = new MetadataExtractionProcessor(extractor, consumer);

processor.

process(Path.of("report.pdf")); // extract → publish
```

---

### `RestMetadataPublisher`

A built-in `MetadataConsumer` that POSTs metadata to an HTTP endpoint using the
JDK's `java.net.http.HttpClient`. Thread-safe; usable in a consumer chain.

```java
import io.github.posseidon.core.ingest.processor.RestMetadataPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

var mapper = new ObjectMapper();
var publisher = new RestMetadataPublisher(
        URI.create("https://api.example.com/metadata"),
        meta -> mapper.writeValueAsString(meta));   // any serializer function

// Use alone
var processor = new MetadataExtractionProcessor(extractor, publisher);

// Or compose with more consumers
MetadataConsumer pipeline = publisher.andThen(auditLogger);
var processor2 = new MetadataExtractionProcessor(extractor, pipeline);
```

A non-2xx response is logged as a warning; no exception is thrown.

---

### Full pipeline example

```java
var extractor = new MetadataExtractor(new Sha256Hash(), new ContentTypeDetector());
var publisher = new RestMetadataPublisher(
        URI.create("https://ingest.example.com/files"),
        meta -> objectMapper.writeValueAsString(meta));
var auditLog = (MetadataConsumer) meta ->
        Files.writeString(auditPath, meta.sha256() + "\n", APPEND);

var consumer = publisher.andThen(auditLog);                // chain consumers
var processor = new MetadataExtractionProcessor(extractor, consumer);

new

FileDiscoveryOrchestrator(processor)
        .

discoverAndProcess(Path.of("/vault"));
```

---

### Real-time directory watching

`DirectoryWatcher` reacts to new or modified files using the JDK `WatchService`,
processing each event on a dedicated virtual thread.

```java
import io.github.posseidon.core.ingest.DirectoryWatcher;

try(var watcher = new DirectoryWatcher(processor)){
        watcher.

startWatching(Path.of("/inbox"));
        Thread.

sleep(Duration.ofHours(8)); // keep process alive
        }
```

`DirectoryWatcher` accepts the same varargs processors as `FileDiscoveryOrchestrator`.

---

### Storage abstractions

`SourceResolver` and `OutputSink` decouple reading and writing from a specific
backend. `LocalFileSystemStorage` implements both for the local filesystem.

```java
import io.github.posseidon.core.storage.local.LocalFileSystemStorage;
import io.github.posseidon.core.reference.LocalFileReference;

var storage = new LocalFileSystemStorage();
var ref = new LocalFileReference(Path.of("out/result.bin"));

// Write
StoredObject stored = storage.write(ref, inputStream);
System.out.

println(stored.name() +": "+stored.

sizeBytes() +" bytes");

// Read
        try(
InputStream in = storage.openStream(ref)){
        // consume in
        }

long bytes = storage.size(ref);
```

Implement `OutputSink` + `SourceResolver` to add cloud or network backends
(Azure Blob, S3, etc.) without touching any other class. Override
`OutputSink.isRemote()` to return `true` and `PdfChunkService` will
automatically route writes through a virtual-thread I/O pool.

---

## pdf-utility

### Maven dependency

```xml

<dependency>
    <groupId>io.github.posseidon</groupId>
    <artifactId>pdf-utility</artifactId>
    <version>0.1.2-SNAPSHOT</version>
</dependency>
```

`pdf-utility` transitively pulls in `file-utility`.

---

### Split a PDF by page ranges

```java
import io.github.posseidon.core.storage.local.LocalFileSystemStorage;
import io.github.posseidon.pdf.chunk.PdfChunkService;
import io.github.posseidon.pdf.chunk.PdfChunk;
import io.github.posseidon.pdf.chunk.model.ChunkFixture;
import io.github.posseidon.pdf.chunk.model.ChunkSpec;

var service = new PdfChunkService(new LocalFileSystemStorage());

var fixture = new ChunkFixture(
        "report.pdf",
        "out/chunks",
        List.of(
                new ChunkSpec("intro", "1-3"),    // pages 1-3
                new ChunkSpec("body", "4-10"),   // pages 4-10
                new ChunkSpec("appendix", "11-")     // page 11 to end
        ));

List<PdfChunk> manifest = service.chunk(List.of(fixture));

manifest.

forEach(c ->
        System.out.

printf("%s  pages=%d  bytes=%d%n",
       c.storedObject().

name(),c.

range().

pageCount(),c.

storedObject().

sizeBytes()));
```

Output files are written to `out/chunks/` as:
`intro_001_p1-3.pdf`, `body_002_p4-10.pdf`, `appendix_003_p11-N.pdf`

#### Page range syntax

| Token | Meaning                                  |
|-------|------------------------------------------|
| `1-3` | Pages 1 through 3 (inclusive, 1-based)   |
| `5`   | Page 5 only (equivalent to `5-5`)        |
| `11-` | Page 11 through the last page of the PDF |

- Ends beyond the last page are clamped silently.
- A start beyond the last page raises an exception.

#### Processing multiple PDFs in parallel

```java
List<ChunkFixture> fixtures = List.of(
        new ChunkFixture("vol1.pdf", "out/vol1", specs1),
        new ChunkFixture("vol2.pdf", "out/vol2", specs2),
        new ChunkFixture("vol3.pdf", "out/vol3", specs3));

List<PdfChunk> all = service.chunk(fixtures);
```

All fixtures and all page ranges within each fixture are processed concurrently
on a CPU-bound thread pool. For remote `OutputSink` implementations, writes are
offloaded to a separate virtual-thread I/O pool automatically.

---

## Extension points

| Interface             | Implement to…                                                |
|-----------------------|--------------------------------------------------------------|
| `Hasher`              | Swap the hash algorithm (MD5, BLAKE3, etc.)                  |
| `ContentTypeDetector` | Replace Tika with a custom MIME detector                     |
| `FileProcessor`       | Add any per-file step to the processing chain                |
| `MetadataConsumer`    | Add any post-extraction sink (database, queue, audit log, …) |
| `OutputSink`          | Write chunks or stored objects to a custom backend           |
| `SourceResolver`      | Read from a custom backend (cloud, network share, …)         |

---

## Thread safety

All production classes in both modules are thread-safe:

- `ContentTypeDetector` — wraps a shared static `Tika` instance (documented thread-safe).
- `Sha256Hash` — creates a new `MessageDigest` per call; no shared mutable state.
- `MetadataExtractor` — all fields are `final`; `AutoDetectParser` is a shared static (thread-safe by Tika spec).
- `MetadataExtractionProcessor` — all fields are `final`; delegates to the above.
- `RestMetadataPublisher` — all fields are `final`; `HttpClient` is designed for concurrent use.
- `FileDiscoveryOrchestrator` — submits one virtual thread per file via `invokeAll`; no shared mutable state.
- `PdfChunkService` — uses `CompletableFuture` with per-thread `PDDocument` instances (one `ThreadLocal` per fixture).
