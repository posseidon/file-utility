package io.github.posseidon.pdf.chunk.helper;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * JUnit 5 extension that prints each test's wall-clock duration to stdout.
 * Register with {@code @ExtendWith(TimingExtension.class)} on the test class.
 */
public final class TimingExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Namespace NS = Namespace.create(TimingExtension.class);
    private static final String START_KEY = "startNanos";

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getStore(NS).put(START_KEY, System.nanoTime());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        long startNanos = context.getStore(NS).remove(START_KEY, long.class);
        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        System.out.printf("[TIMER] %-80s  %.3f ms%n", context.getDisplayName(), elapsedMs);
    }
}
