package io.github.posseidon.pdf.chunk;

import io.github.posseidon.pdf.PdfUtilityException;
import io.github.posseidon.pdf.chunk.model.PageContent;
import io.github.posseidon.pdf.chunk.model.PageRange;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Extracts one page range from an already-opened {@link PDDocument}.
 * <p>
 * Designed to be submitted to an {@link java.util.concurrent.ExecutorService}
 * for parallel chunking: one task per range, all sharing the same source document.
 * <p>
 * Note: PDFBox does not guarantee thread-safety on {@code PDDocument}. For safe
 * concurrent use, open the source from an in-memory byte array
 * ({@code Loader.loadPDF(byte[])}) rather than a file, which avoids shared
 * file-cursor state in the underlying {@code RandomAccessRead}.
 */
public final class PdfSplitTask implements Callable<PageContent> {

    private final PDDocument document;
    private final PageRange range;

    public PdfSplitTask(PDDocument document, PageRange range) {
        this.document = Objects.requireNonNull(document, "document");
        this.range = Objects.requireNonNull(range, "range");
    }

    @Override
    public PageContent call() throws IOException {
        int total = document.getNumberOfPages();
        if (range.end() > total) {
            throw new PdfUtilityException(
                    "Range " + range.start() + "-" + range.end()
                            + " exceeds document page count " + total);
        }
        try (PDDocument target = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int page = range.start(); page <= range.end(); page++) {
                target.importPage(document.getPage(page - 1));
            }
            target.save(out);
            return new PageContent(range, out.toByteArray());
        }
    }
}
