package io.github.posseidon.core.util;

import io.github.posseidon.core.detect.ContentTypeDetector;
import io.github.posseidon.core.model.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public final class FileUtility {

    private FileUtility() {}

    /**
     * Returns a predicate that accepts only non-hidden, non-source-code files.
     * Source code detection is delegated to Tika (magic-byte sniff) via {@link ContentTypeDetector},
     * so the result tracks Tika's MIME database rather than a hardcoded extension list.
     */
    public static Predicate<Path> processableFileFilter(ContentTypeDetector detector) {
        return path -> !isHidden(path)
                && MediaType.fromMime(detector.detect(path)) != MediaType.CODE;
    }

    public static boolean isHidden(Path path) {
        // dotfile check avoids IOException from Files.isHidden() on broken symlinks
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        if (name.startsWith(".")) return true;
        try {
            return Files.isHidden(path);
        } catch (IOException e) {
            return false;
        }
    }

    public static String readOwner(Path path) {
        try {
            FileOwnerAttributeView view = Files.getFileAttributeView(path, FileOwnerAttributeView.class);
            return view == null ? "unknown" : view.getOwner().getName();
        } catch (IOException e) {
            return "unknown";
        }
    }

    public static String extension(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot < 0 || dot == name.length() - 1) ? "" : name.substring(dot + 1).toLowerCase();
    }

    public static List<String> extractDirectories(Path path) {
        if (path == null) {
            throw new NullPointerException("Path cannot be null");
        }
        if (path.toString().isEmpty()) {
            return List.of();
        }
        return StreamSupport.stream(
                        path.spliterator(),
                        false
                )
                .map(Path::toString)
                .toList();
    }
}
