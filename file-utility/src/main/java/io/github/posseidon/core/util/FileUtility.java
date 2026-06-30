package io.github.posseidon.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.util.List;
import java.util.stream.StreamSupport;

public final class FileUtility {
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
