package io.github.posseidon.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileOwnerAttributeView;

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
}
