package be.fanotmz.docsearch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DeterministicEmbeddingTestConfiguration.class)
@Testcontainers
class DocumentLibraryIntegrationTest {
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

    @BeforeEach
    void cleanData() {
        jdbc.update("DELETE FROM docsearch_vector_store");
        jdbc.update("DELETE FROM ingestion_jobs");
        jdbc.update("DELETE FROM document_pages");
        jdbc.update("DELETE FROM documents");
    }

    @Test
    void returnsAnEmptyLibraryForANewInstallation() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()" ).value(0));
    }

    @Test
    void returnsReadyAndFailedDocumentSummaries() throws Exception {
        Instant now = Instant.parse("2026-10-07T10:00:00Z");
        UUID ready = insertDocument(UUID.fromString("00000000-0000-0000-0000-000000000001"), "READY", "ready.pdf", 3, now);
        UUID failed = insertDocument(UUID.fromString("00000000-0000-0000-0000-000000000002"), "FAILED", "failed.pdf", 0, now.minusSeconds(60));

        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()" ).value(2))
                .andExpect(jsonPath("$[0].documentId").value(ready.toString()))
                .andExpect(jsonPath("$[0].filename").value("ready.pdf"))
                .andExpect(jsonPath("$[0].pageCount").value(3))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-07T10:00:00Z"))
                .andExpect(jsonPath("$[1].documentId").value(failed.toString()))
                .andExpect(jsonPath("$[1].status").value("FAILED"));
    }

    @Test
    void usesDocumentIdAsDeterministicTieBreakerForNewestFirstOrdering() throws Exception {
        Instant now = Instant.parse("2026-10-07T10:00:00Z");
        UUID lower = insertDocument(UUID.fromString("00000000-0000-0000-0000-000000000001"), "PROCESSING", "lower.pdf", 0, now);
        UUID higher = insertDocument(UUID.fromString("00000000-0000-0000-0000-000000000002"), "PROCESSING", "higher.pdf", 0, now);

        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(higher.toString()))
                .andExpect(jsonPath("$[1].documentId").value(lower.toString()));
    }

    @Test
    void reportsTheFunctionalLocalMvpCapabilities() throws Exception {
        mockMvc.perform(get("/api/v1/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("LOCAL_MVP"))
                .andExpect(jsonPath("$.documentSearchAvailable").value(true));
    }

    private UUID insertDocument(UUID id, String status, String filename, int pageCount, Instant createdAt) {
        Timestamp timestamp = Timestamp.from(createdAt);
        jdbc.update("""
                INSERT INTO documents (id, original_filename, content_type, page_count, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, filename, "application/pdf", pageCount, status, timestamp, timestamp);
        return id;
    }
}
