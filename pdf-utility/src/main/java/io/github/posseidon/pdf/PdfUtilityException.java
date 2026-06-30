package io.github.posseidon.pdf;

import io.github.posseidon.core.FileUtilityException;

/**
 * Unchecked exception for failures within the pdf-utility module.
 */
public class PdfUtilityException extends FileUtilityException {

    public PdfUtilityException(String message) {
        super(message);
    }

    public PdfUtilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
