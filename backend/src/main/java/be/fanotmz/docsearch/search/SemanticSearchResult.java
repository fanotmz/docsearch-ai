package be.fanotmz.docsearch.search;

import java.util.UUID;

public record SemanticSearchResult(
        UUID documentId,
        String source,
        int pageNumber,
        int chunkIndex,
        String content,
        Double score) {
}
