package io.github.posseidon.core.classify;

import io.github.posseidon.core.model.DocumentInsights;

import java.util.concurrent.CompletableFuture;

public interface DocumentTypeClassifier {
    CompletableFuture<DocumentInsights> analyzeAsync(String text);
}
