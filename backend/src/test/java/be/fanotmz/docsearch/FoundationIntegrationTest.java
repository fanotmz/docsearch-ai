package be.fanotmz.docsearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
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
}
