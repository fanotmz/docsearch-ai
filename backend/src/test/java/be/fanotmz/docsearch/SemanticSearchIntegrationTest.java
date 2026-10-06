package be.fanotmz.docsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DeterministicEmbeddingTestConfiguration.class)
@Testcontainers
class SemanticSearchIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.1-pg16-bookworm")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.ai.ollama.base-url", () -> "http://127.0.0.1:1");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired VectorStore vectorStore;

    @BeforeEach
    void cleanSearchData() {
        jdbc.update("DELETE FROM docsearch_vector_store");
        jdbc.update("DELETE FROM ingestion_jobs");
        jdbc.update("DELETE FROM document_pages");
        jdbc.update("DELETE FROM documents");
    }

    @Test
    void rejectsBlankAndInvalidQueries() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .contentType("application/json")
                        .content("{\"query\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"));

        mockMvc.perform(post("/api/v1/search")
                        .contentType("application/json")
                        .content("{\"query\":\"database\",\"topK\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOP_K"));

        mockMvc.perform(post("/api/v1/search")
                        .contentType("application/json")
                        .content("{\"query\":\"database\",\"topK\":21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOP_K"));

        mockMvc.perform(post("/api/v1/search")
                        .contentType("application/json")
                        .content("{\"query\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUERY_TOO_LONG"));
    }

    @Test
    void returnsEmptyResultsWhenNoReadyDocumentExists() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .contentType("application/json")
                        .content("{\"query\":\"database\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("database"))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void returnsOnlyReadyResultsInVectorStoreOrderWithMetadataAndScore() throws Exception {
        UUID databaseDocument = insertDocument("READY");
        UUID birdDocument = insertDocument("READY");
        UUID failedDocument = insertDocument("FAILED");
        addVector(databaseDocument, "database.pdf", 2, 0, "PostgreSQL and pgvector database facts");
        addVector(birdDocument, "birds.pdf", 3, 1, "Migratory bird facts");
        addVector(failedDocument, "failed.pdf", 1, 0, "PostgreSQL database should be hidden");

        String response = mockMvc.perform(post("/api/v1/search")
                        .contentType("application/json")
                        .content("{\"query\":\"database\",\"topK\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].documentId").value(databaseDocument.toString()))
                .andExpect(jsonPath("$.results[0].source").value("database.pdf"))
                .andExpect(jsonPath("$.results[0].pageNumber").value(2))
                .andExpect(jsonPath("$.results[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.results[0].content").value("PostgreSQL and pgvector database facts"))
                .andExpect(jsonPath("$.results[0].score").isNumber())
                .andExpect(jsonPath("$.results[1].documentId").value(birdDocument.toString()))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(failedDocument.toString());
    }

    @Test
    void respectsTopKBound() throws Exception {
        UUID first = insertDocument("READY");
        UUID second = insertDocument("READY");
        addVector(first, "first.pdf", 1, 0, "PostgreSQL database");
        addVector(second, "second.pdf", 1, 0, "Migratory birds");

        mockMvc.perform(post("/api/v1/search")
                        .contentType("application/json")
                        .content("{\"query\":\"database\",\"topK\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].documentId").value(first.toString()));
    }

    private UUID insertDocument(String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO documents (id, original_filename, content_type, page_count, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, status.toLowerCase() + ".pdf", "application/pdf", 1, status, now, now);
        return id;
    }

    private void addVector(UUID documentId, String source, int pageNumber, int chunkIndex, String content) {
        vectorStore.add(List.of(Document.builder()
                .id(UUID.randomUUID().toString())
                .text(content)
                .metadata(Map.of(
                        "docsearch.document_id", documentId.toString(),
                        "docsearch.source", source,
                        "docsearch.page_number", pageNumber,
                        "docsearch.chunk_index", chunkIndex))
                .build()));
    }
}
