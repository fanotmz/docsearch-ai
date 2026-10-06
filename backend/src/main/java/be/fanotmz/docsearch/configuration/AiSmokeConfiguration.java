package be.fanotmz.docsearch.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "docsearch.smoke.enabled", havingValue = "true")
public class AiSmokeConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiSmokeConfiguration.class);

    @Bean
    ApplicationRunner aiSmoke(
            ChatClient.Builder builder,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.ollama.chat.model:unknown}") String chatModel,
            @Value("${spring.ai.ollama.embedding.model:unknown}") String embeddingModelName) {
        return args -> {
            long embeddingStarted = System.nanoTime();
            float[] vector = embeddingModel.embed("Document search smoke test.");
            long embeddingElapsedMs = (System.nanoTime() - embeddingStarted) / 1_000_000;
            if (vector.length == 0) {
                throw new IllegalStateException("Embedding model returned an empty vector");
            }
            for (float value : vector) {
                if (!Float.isFinite(value)) {
                    throw new IllegalStateException("Embedding model returned a non-finite value");
                }
            }
            LOGGER.info("Embedding smoke passed: model={}, dimensions={}, finiteValues=true, elapsedMs={}",
                    embeddingModelName, vector.length, embeddingElapsedMs);
            long generationStarted = System.nanoTime();
            String answer = builder.build().prompt()
                    .user("Reply with exactly DOCSEARCH_OK, with no explanation.")
                    .call().content();
            long generationElapsedMs = (System.nanoTime() - generationStarted) / 1_000_000;
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("Chat smoke returned empty text");
            }
            LOGGER.info("Chat smoke passed: model={}, nonEmpty=true, elapsedMs={}",
                    chatModel, generationElapsedMs);
        };
    }
}
