package be.fanotmz.docsearch.search;

import java.util.List;
import java.util.UUID;

import be.fanotmz.docsearch.documents.persistence.DocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SemanticSearchService {
    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);
    public static final int DEFAULT_TOP_K = 5;
    public static final int MAX_TOP_K = 20;
    public static final int MAX_QUERY_LENGTH = 2_000;
    private static final String DOCUMENT_ID_METADATA = "docsearch.document_id";
    private static final String SOURCE_METADATA = "docsearch.source";
    private static final String PAGE_NUMBER_METADATA = "docsearch.page_number";
    private static final String CHUNK_INDEX_METADATA = "docsearch.chunk_index";

    private final DocumentRepository documentRepository;
    private final VectorStore vectorStore;

    public SemanticSearchService(DocumentRepository documentRepository, VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
    }

    public SemanticSearchResponse search(SemanticSearchRequest request) {
        String query = validateQuery(request == null ? null : request.query());
        int topK = validateTopK(request == null ? null : request.topK());
        List<UUID> readyDocumentIds = documentRepository.findReadyDocumentIds();
        if (readyDocumentIds.isEmpty()) {
            return new SemanticSearchResponse(query, List.of());
        }

        List<Object> readyMetadataIds = readyDocumentIds.stream().map(Object.class::cast).toList();
        var readyFilter = new FilterExpressionBuilder()
                .in(DOCUMENT_ID_METADATA, readyMetadataIds)
                .build();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThresholdAll()
                .filterExpression(readyFilter)
                .build();
        long searchStarted = System.nanoTime();
        List<SemanticSearchResult> results = vectorStore.similaritySearch(searchRequest).stream()
                .map(this::toResult)
                .toList();
        long searchElapsedMillis = (System.nanoTime() - searchStarted) / 1_000_000;
        log.info("Semantic search returned {} results in {} ms for topK {}", results.size(), searchElapsedMillis, topK);
        return new SemanticSearchResponse(query, results);
    }

    public SemanticSearchResponse search(String query, int topK) {
        return search(new SemanticSearchRequest(query, topK));
    }

    private String validateQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new SearchValidationException("INVALID_QUERY", "Query must not be blank");
        }
        String normalized = query.trim();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new SearchValidationException("QUERY_TOO_LONG", "Query must not exceed 2000 characters");
        }
        return normalized;
    }

    private int validateTopK(Integer topK) {
        int requested = topK == null ? DEFAULT_TOP_K : topK;
        if (requested <= 0 || requested > MAX_TOP_K) {
            throw new SearchValidationException("INVALID_TOP_K", "topK must be between 1 and 20");
        }
        return requested;
    }

    private SemanticSearchResult toResult(Document document) {
        return new SemanticSearchResult(
                UUID.fromString(metadataText(document, DOCUMENT_ID_METADATA)),
                metadataText(document, SOURCE_METADATA),
                metadataInt(document, PAGE_NUMBER_METADATA),
                metadataInt(document, CHUNK_INDEX_METADATA),
                document.getText(),
                document.getScore());
    }

    private String metadataText(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value == null || !StringUtils.hasText(value.toString())) {
            throw new IllegalStateException("Indexed result is missing metadata: " + key);
        }
        return value.toString();
    }

    private int metadataInt(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(metadataText(document, key));
        }
        catch (NumberFormatException exception) {
            throw new IllegalStateException("Indexed result has invalid metadata: " + key, exception);
        }
    }
}
