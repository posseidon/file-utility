package io.github.posseidon.core.detect;

import io.github.posseidon.core.FileUtilityException;
import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** Thin wrapper over Apache Tika for MIME type detection. */
public final class ContentTypeDetector {

    public static final String PDF_MIME_TYPE = "application/pdf";

    private static final Tika TIKA = new Tika();

    /** Detect the MIME type from a stream, using the supplied name as a hint. */
    public String detect(InputStream stream, String name) {
        try {
            return TIKA.detect(stream, name);
        } catch (IOException e) {
            throw new FileUtilityException("Content type detection failed for: " + name, e);
        }
    }

    /** Detect the MIME type of a file on disk. */
    public String detect(Path path) {
        try {
            return TIKA.detect(path);
        } catch (IOException e) {
            throw new FileUtilityException("Content type detection failed for: " + path, e);
        }
    }

    public boolean isPdf(Path path) {
        return PDF_MIME_TYPE.equals(detect(path));
    }
}
