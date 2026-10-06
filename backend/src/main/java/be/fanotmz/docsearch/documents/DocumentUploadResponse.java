package be.fanotmz.docsearch.documents;

import java.util.UUID;

public record DocumentUploadResponse(
        UUID documentId,
        String filename,
        int pageCount,
        DocumentStatus status) {
}
