package be.fanotmz.docsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class DeterministicEmbeddingTestConfiguration {
    @Bean
    @Primary
    EmbeddingModel deterministicEmbeddingModel() {
        return new DeterministicEmbeddingModel();
    }

    static final class DeterministicEmbeddingModel extends AbstractEmbeddingModel {
        private static final int DIMENSIONS = 1024;

        DeterministicEmbeddingModel() {
            this.embeddingDimensions.set(DIMENSIONS);
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> results = new ArrayList<>();
            List<String> instructions = request.getInstructions();
            for (int index = 0; index < instructions.size(); index++) {
                results.add(new Embedding(vector(instructions.get(index)), index));
            }
            return new EmbeddingResponse(results);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        private float[] vector(String text) {
            float[] vector = new float[DIMENSIONS];
            String normalized = text.toLowerCase(Locale.ROOT);
            if (normalized.contains("database") || normalized.contains("postgresql")
                    || normalized.contains("pgvector")) {
                vector[0] = 1.0f;
            }
            else if (normalized.contains("bird") || normalized.contains("migration")) {
                vector[0] = 0.6f;
                vector[1] = 0.8f;
            }
            else {
                vector[2] = 1.0f;
            }
            return vector;
        }
    }
}
