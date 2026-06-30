package io.github.posseidon.core;

/**
 * Unchecked exception for failures within the file-utility library.
 */
public class FileUtilityException extends RuntimeException {

    public FileUtilityException(String message) {
        super(message);
    }

    public FileUtilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
