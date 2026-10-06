package be.fanotmz.docsearch.qa;

import java.util.UUID;

public record GroundedCitation(
        String id,
        UUID documentId,
        String source,
        int pageNumber,
        int chunkIndex) {
}
