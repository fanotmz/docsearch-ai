package be.fanotmz.docsearch.search;

import java.util.List;

public record SemanticSearchResponse(String query, List<SemanticSearchResult> results) {
}
