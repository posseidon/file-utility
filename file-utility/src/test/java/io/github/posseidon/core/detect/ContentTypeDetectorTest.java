package io.github.posseidon.core.detect;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentTypeDetectorTest {

    @Test
    void detectsPlainTextByContentAndName() {
        ContentTypeDetector detector = new ContentTypeDetector();
        String type = detector.detect(
                new ByteArrayInputStream("hello, world".getBytes(StandardCharsets.UTF_8)),
                "notes.txt");
        assertEquals("text/plain", type);
    }
}
