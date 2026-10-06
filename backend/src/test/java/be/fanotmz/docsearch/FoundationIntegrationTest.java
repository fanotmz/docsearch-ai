package be.fanotmz.docsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FoundationIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.1-pg16-bookworm")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // No inference call should be needed to start the foundation.
        registry.add("spring.ai.ollama.base-url", () -> "http://127.0.0.1:1");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ChatClient.Builder chatClientBuilder;
    @Autowired EmbeddingModel embeddingModel;
    @Autowired MockMvc mockMvc;

    @Test
    void startsWithNativeAiClientsAndMigratesTheVectorExtensionWithoutOllama() {
        assertThat(chatClientBuilder).isNotNull();
        assertThat(embeddingModel).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class))
                .isEqualTo("0.8.1");
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector", Double.class))
                .isEqualTo(1.0);
    }

    @Test
    void uploadsAValidPdfAndPersistsOneNormalizedPagePerSpringAiDocument() throws Exception {
        byte[] pdf = new ClassPathResource("fixtures/docsearch-03-pages.pdf").getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fixture.pdf", "application/pdf", pdf);

        String response = mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.filename").value("fixture.pdf"))
                .andExpect(jsonPath("$.pageCount").value(3))
                .andExpect(jsonPath("$.status").value("READY"))
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(JsonPath.read(response, "$.documentId"));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM documents WHERE id = ?", String.class, documentId)).isEqualTo("READY");
        assertThat(jdbc.queryForObject(
                "SELECT page_count FROM documents WHERE id = ?", Integer.class, documentId)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM ingestion_jobs WHERE document_id = ?", String.class, documentId))
                .isEqualTo("SUCCEEDED");

        List<Map<String, Object>> pages = jdbc.queryForList(
                "SELECT page_number, source, content FROM document_pages WHERE document_id = ? ORDER BY page_number",
                documentId);
        assertThat(pages).hasSize(3);
        assertThat(pages).extracting(page -> page.get("page_number")).containsExactly(1, 2, 3);
        assertThat(pages).extracting(page -> page.get("source"))
                .containsExactly("fixture.pdf", "fixture.pdf", "fixture.pdf");
        assertThat(pages.get(0).get("content")).asString()
                .contains("DOCSEARCH_PAGE_ONE_ALPHA")
                .doesNotContain("DOCSEARCH_PAGE_TWO_BETA", "DOCSEARCH_PAGE_THREE_GAMMA");
        assertThat(pages.get(1).get("content")).asString()
                .contains("DOCSEARCH_PAGE_TWO_BETA")
                .doesNotContain("DOCSEARCH_PAGE_ONE_ALPHA", "DOCSEARCH_PAGE_THREE_GAMMA");
        assertThat(pages.get(2).get("content")).asString()
                .contains("DOCSEARCH_PAGE_THREE_GAMMA")
                .doesNotContain("DOCSEARCH_PAGE_ONE_ALPHA", "DOCSEARCH_PAGE_TWO_BETA");
    }

    @Test
    void rejectsEmptyAndNonPdfUploadsDeterministically() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "note.txt", "text/plain", "not a PDF".getBytes())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void rejectsPdfWithoutExtractableTextThroughTheApiAndPersistsFailedLifecycle() throws Exception {
        byte[] pdf = new ClassPathResource("fixtures/docsearch-03-no-text.pdf").getInputStream().readAllBytes();
        long documentsBefore = jdbc.queryForObject("SELECT count(*) FROM documents", Long.class);

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "no-text.pdf", "application/pdf", pdf)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_EXTRACTABLE_TEXT"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents", Long.class))
                .isEqualTo(documentsBefore + 1);
        assertLatestFailedLifecycle("NO_EXTRACTABLE_TEXT");
    }

    @Test
    void malformedPdfReturns422ThroughTheApiAndPersistsFailedLifecycle() throws Exception {
        long documentsBefore = jdbc.queryForObject("SELECT count(*) FROM documents", Long.class);

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile(
                                "file", "broken.pdf", "application/pdf", "%PDF-1.4\nnot complete".getBytes())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MALFORMED_PDF"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents", Long.class))
                .isEqualTo(documentsBefore + 1);
        assertLatestFailedLifecycle("MALFORMED_PDF");
    }

    @Test
    void rejectsPdfContentSpoofedByTheDeclaredMediaType() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile(
                                "file", "spoofed.pdf", "application/pdf", "plain text".getBytes())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("NOT_PDF"));
    }

    private void assertLatestFailedLifecycle(String errorCode) {
        Map<String, Object> failed = jdbc.queryForMap(
                "SELECT d.status AS document_status, j.status AS job_status, j.error_code "
                        + "FROM documents d JOIN ingestion_jobs j ON j.document_id = d.id "
                        + "ORDER BY d.created_at DESC LIMIT 1");
        assertThat(failed.get("document_status")).isEqualTo("FAILED");
        assertThat(failed.get("job_status")).isEqualTo("FAILED");
        assertThat(failed.get("error_code")).isEqualTo(errorCode);
    }
}
