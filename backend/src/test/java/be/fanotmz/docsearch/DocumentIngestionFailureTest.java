package be.fanotmz.docsearch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import be.fanotmz.docsearch.documents.persistence.DocumentPageRepository;
import be.fanotmz.docsearch.documents.persistence.DocumentRepository;
import be.fanotmz.docsearch.ingestion.DocumentIngestionService;
import be.fanotmz.docsearch.ingestion.IngestionJobRepository;
import be.fanotmz.docsearch.ingestion.PageChunkingService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

class DocumentIngestionFailureTest {
    @Test
    void indexingFailureCleansPartialVectorIdsAndFailsTheLifecycle() throws Exception {
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentPageRepository pages = mock(DocumentPageRepository.class);
        IngestionJobRepository jobs = mock(IngestionJobRepository.class);
        PageChunkingService chunking = mock(PageChunkingService.class);
        VectorStore vectorStore = mock(VectorStore.class);
        Document chunk = Document.builder().id(UUID.randomUUID().toString()).text("chunk").build();
        doThrow(new IllegalStateException("embedding unavailable")).when(vectorStore).add(anyList());

        DocumentIngestionService service = new DocumentIngestionService(
                documents, pages, jobs, chunking, vectorStore);
        byte[] pdf = new ClassPathResource("fixtures/docsearch-03-pages.pdf").getInputStream().readAllBytes();
        org.mockito.Mockito.when(pages.findByDocumentId(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new DocumentPageRepository.StoredPage(1, "fixture.pdf", "text")));
        org.mockito.Mockito.when(chunking.chunk(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(chunk));

        assertThatThrownBy(() -> service.ingest(
                new MockMultipartFile("file", "fixture.pdf", "application/pdf", pdf)))
                .hasMessageContaining("could not be indexed");

        verify(vectorStore).delete(List.of(chunk.getId()));
        verify(documents).markFailed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(jobs).markFailed(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("INDEXING_FAILED"),
                org.mockito.ArgumentMatchers.contains("could not be indexed"),
                org.mockito.ArgumentMatchers.any());
    }
}
