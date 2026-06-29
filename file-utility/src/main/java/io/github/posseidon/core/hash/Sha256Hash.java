package io.github.posseidon.core.hash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class Sha256Hash implements Hasher {
    private static final int BUFFER = 8_192;
    public static final String SHA_256 = "SHA-256";

    @Override
    public String hash(Path path) throws IOException, InterruptedException {
        MessageDigest digest = newDigest();
        byte[] buf = new byte[BUFFER];
        try (InputStream in = Files.newInputStream(path)) {
            int n;
            while ((n = in.read(buf)) != -1) {
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedException("Interrupted hashing " + path);
                }
                digest.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
