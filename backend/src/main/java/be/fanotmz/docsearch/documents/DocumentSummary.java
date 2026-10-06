package be.fanotmz.docsearch.documents;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummary(
        UUID documentId,
        String filename,
        int pageCount,
        DocumentStatus status,
        Instant createdAt) {
}
