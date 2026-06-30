package io.github.posseidon.core.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

public record FileMetadata(
        String origin,
        Path path,
        String name,
        String extension,
        long size,
        Instant createdAt,
        Instant modifiedAt,
        String owner,
        String mimeType,
        MediaType mediaType,
        String sha256,
        Collection<String> directories,
        Map<String, String> extra
) {}
