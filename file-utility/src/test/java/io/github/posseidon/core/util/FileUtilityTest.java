package io.github.posseidon.core.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static io.github.posseidon.core.util.FileUtility.extractDirectories;
import static org.junit.jupiter.api.Assertions.*;

class FileUtilityTest {

    @Test
    void testExtractDirectories_AbsolutePath() {
        Path path = Path.of("/home/user/projects");
        Collection<String> result = extractDirectories(path);
        assertIterableEquals(List.of("home", "user", "projects"), result);
    }

    @Test
    void testExtractDirectories_RelativePath() {
        Path path = Path.of("a/b/c");
        Collection<String> result = extractDirectories(path);
        assertIterableEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void testExtractDirectories_SingleDirectory() {
        Path path = Path.of("single");
        Collection<String> result = extractDirectories(path);
        assertIterableEquals(List.of("single"), result);
    }

    @Test
    void testExtractDirectories_EmptyPath() {
        Path path = Path.of("");
        Collection<String> result = extractDirectories(path);
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractDirectories_PathWithFile() {
        Path path = Path.of("/dir1/dir2/file.txt");
        Collection<String> result = extractDirectories(path);
        assertIterableEquals(List.of("dir1", "dir2", "file.txt"), result);
    }

    @Test
    void testExtractDirectories_RootOnly() {
        Path path = Path.of("/");
        Collection<String> result = extractDirectories(path);
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractDirectories_WindowsPath() {
        Path path = Path.of("C:", "Users", "Test", "Documents");
        Collection<String> result = extractDirectories(path);
        assertIterableEquals(List.of("C:", "Users", "Test", "Documents"), result);
    }

    @Test
    void testExtractDirectories_PathWithDots() {
        Path path = Path.of("/a/./b/../c");
        Collection<String> result = extractDirectories(path);
        assertIterableEquals(List.of("a", ".", "b", "..", "c"), result);
    }

    @Test
    void testExtractDirectories_NullPath() {
        assertThrows(NullPointerException.class, () -> extractDirectories(null));
    }
}

