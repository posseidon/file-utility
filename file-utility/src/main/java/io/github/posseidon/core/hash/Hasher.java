package io.github.posseidon.core.hash;

import java.io.IOException;
import java.nio.file.Path;

public interface Hasher {
    String hash(Path path) throws IOException, InterruptedException;
}
