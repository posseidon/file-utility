# file-utility

A small, framework-free Java 21 library for file work, plus a `pdf-utility`
module that splits PDFs into smaller PDFs by page range. Both modules are
plain libraries you can depend on from other projects (for example, a future
Spring AI MCP server module).

## Modules

| Module         | Artifact                       | Purpose                                                        | Key dependency      |
|----------------|--------------------------------|---------------------------------------------------------------|---------------------|
| `file-utility` | `com.fileutility:file-utility` | Storage abstractions (`SourceResolver` / `OutputSink`), local filesystem implementation, and content-type detection. | Apache **Tika** 3.3.1 |
| `pdf-utility`  | `com.fileutility:pdf-utility`  | PDF **chunker**: split a PDF into smaller PDFs by 1-based, inclusive page ranges. Depends on `file-utility`. | Apache **PDFBox** 3.0.7 (+ xmpbox) |

The reactor root (`file-utility-parent`, packaging `pom`) only manages versions
and lists modules. The reusable library jar is the `file-utility` module of the
same name nested beneath it.

## Build

Requires **JDK 21** and **Maven 3.9+**.

```bash
mvn clean install
```

This compiles both modules, runs the unit tests, and installs the jars into your
local `~/.m2` repository so other projects can depend on them. No Maven wrapper
is bundled; to add one once the project is open, run `mvn -N wrapper:wrapper`.

> The project was scaffolded without a build run (the authoring sandbox had no
> Maven Central access), so do a `mvn clean install` first to pull dependencies
> and confirm the tests pass.

## Use it from another project

```xml
<dependency>
    <groupId>com.fileutility</groupId>
    <artifactId>pdf-utility</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Split a PDF by page ranges

```java
import com.fileutility.core.storage.local.LocalFileSystemStorage;
import com.fileutility.pdf.chunk.LocalChunkNaming;
import com.fileutility.pdf.chunk.PdfChunk;
import com.fileutility.pdf.chunk.PdfChunkService;

import java.nio.file.Path;
import java.util.List;

var service = new PdfChunkService(new LocalFileSystemStorage());

List<PdfChunk> manifest = service.chunk(
        Path.of("report.pdf"),
        "1-3,4-10,11-",                                   // ranges; "11-" runs to the last page
        new LocalChunkNaming(Path.of("out"), "report"));  // out/report_001_p1-3.pdf, ...

manifest.forEach(c ->
        System.out.println(c.name() + " -> pages " + c.pageCount()
                + " (" + c.sizeBytes() + " bytes)"));
```

## Design notes

- **Page ranges** are 1-based and inclusive. Tokens may be `a-b`, a bare page
  `n` (means `n-n`), or open-ended `a-` (runs to the last page). Ends past the
  document are clamped; a start past the document is rejected.
- **`PdfChunker`** is pure: it returns chunk bytes and performs no I/O, so it is
  trivial to unit-test and to drive from a worker pool.
- **`PdfChunkService`** wires the chunker to an `OutputSink`, so swapping local
  disk for another backend (e.g. Azure Blob) is a new `OutputSink` /
  `SourceResolver` implementation in a separate module — no change here.
- `xmpbox` is included for upcoming PDF metadata/XMP features.

## Next module

The planned MCP server (Java 21 + Spring AI MCP, async submit/poll) would be a
third module depending on `pdf-utility`, exposing `pdf_chunk_submit/status/result`
as `@McpTool` methods. The library stays framework-free so it remains reusable.

## Coordinates

Group/package use the placeholder `com.fileutility`; rename freely before publishing.
