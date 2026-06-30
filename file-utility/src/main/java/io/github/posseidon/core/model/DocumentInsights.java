package io.github.posseidon.core.model;

import java.util.List;
import java.util.Map;

/**
 * Structured information extracted from a document by an LLM in a single inference call.
 * All collection fields are immutable. Nullable fields are null when the model could not
 * determine a value or when the instance was created via {@link #ofDocumentType}.
 */
public record DocumentInsights(
        String documentType,
        String language,
        String summary,
        List<String> tags,
        boolean actionRequired,
        String actionDeadline,
        Map<String, String> keyDates,
        List<String> amounts,
        List<String> organizations,
        List<String> people,
        boolean containsPii
) {
    public DocumentInsights {
        tags = List.copyOf(tags);
        amounts = List.copyOf(amounts);
        organizations = List.copyOf(organizations);
        people = List.copyOf(people);
        keyDates = Map.copyOf(keyDates);
    }

    /**
     * Degraded instance used as a fallback when full JSON parsing fails.
     */
    public static DocumentInsights ofDocumentType(String documentType) {
        return new DocumentInsights(
                documentType, null, null,
                List.of(), false, null, Map.of(), List.of(), List.of(), List.of(),
                false);
    }
}
