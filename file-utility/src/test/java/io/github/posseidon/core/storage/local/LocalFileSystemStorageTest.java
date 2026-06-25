package io.github.posseidon.core.storage.local;

import io.github.posseidon.core.reference.LocalFileReference;
import io.github.posseidon.core.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalFileSystemStorageTest {

    @Test
    void writesThenReadsBack(@TempDir Path tmp) throws IOException {
        LocalFileSystemStorage storage = new LocalFileSystemStorage();
        Path target = tmp.resolve("sub/out.bin");
        byte[] data = "payload".getBytes(StandardCharsets.UTF_8);

        StoredObject stored = storage.write(
                new LocalFileReference(target), new ByteArrayInputStream(data));

        assertEquals(data.length, stored.sizeBytes());
        try (InputStream in = storage.openStream(new LocalFileReference(target))) {
            assertArrayEquals(data, in.readAllBytes());
        }
    }
}
