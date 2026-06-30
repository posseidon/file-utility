package io.github.posseidon.core.util;

public final class StringUtility {

    private StringUtility() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "";

        // Take only the first line, then the first token (word) to discard preamble sentences.
        String first = raw.strip().lines().findFirst().orElse("").strip();

        return first
                .toLowerCase()
                .replaceAll("[-\\s]+", "_")       // spaces and hyphens → underscore
                .replaceAll("[^a-z0-9_]", "")     // strip anything else
                .replaceAll("_+", "_")             // collapse consecutive underscores
                .replaceAll("^_|_$", "");          // strip leading/trailing underscores
    }
}
