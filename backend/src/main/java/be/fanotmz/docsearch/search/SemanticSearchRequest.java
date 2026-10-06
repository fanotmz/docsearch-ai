package be.fanotmz.docsearch.search;

public record SemanticSearchRequest(String query, Integer topK) {
}
