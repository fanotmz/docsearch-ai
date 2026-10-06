package be.fanotmz.docsearch.configuration;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfiguration {
    public static final int EMBEDDING_DIMENSIONS = 1024;
    public static final String VECTOR_TABLE = "docsearch_vector_store";

    @Bean
    PgVectorStore pgVectorStore(JdbcTemplate jdbc, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbc, embeddingModel)
                .schemaName("public")
                .vectorTableName(VECTOR_TABLE)
                .idType(PgVectorStore.PgIdType.UUID)
                .dimensions(EMBEDDING_DIMENSIONS)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.NONE)
                .vectorTableValidationsEnabled(true)
                .initializeSchema(false)
                .build();
    }
}
