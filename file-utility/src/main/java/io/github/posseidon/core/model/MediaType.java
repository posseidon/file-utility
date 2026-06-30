package io.github.posseidon.core.model;

public enum MediaType {
    IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, TEXT, CODE, DATA, OTHER;

    public static MediaType fromMime(String mime) {
        if (mime == null || mime.isBlank()) return OTHER;
        if (mime.startsWith("image/")) return IMAGE;
        if (mime.startsWith("video/")) return VIDEO;
        if (mime.startsWith("audio/")) return AUDIO;
        // text/x-java-source, text/x-python, text/x-sh, etc.
        if (mime.startsWith("text/x-")) return CODE;
        if (mime.startsWith("text/")) return TEXT;
        return switch (mime) {
            case "application/pdf",
                 "application/msword",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.ms-excel",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.ms-powerpoint",
                 "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> DOCUMENT;
            case "application/zip",
                 "application/x-tar",
                 "application/gzip",
                 "application/x-bzip2",
                 "application/x-7z-compressed",
                 "application/x-rar-compressed",
                 "application/java-archive" -> ARCHIVE;
            case "application/json",
                 "application/xml",
                 "application/csv",
                 "application/x-ndjson" -> DATA;
            case "application/javascript",
                 "application/x-sh",
                 "application/x-httpd-php" -> CODE;
            default -> OTHER;
        };
    }
}

