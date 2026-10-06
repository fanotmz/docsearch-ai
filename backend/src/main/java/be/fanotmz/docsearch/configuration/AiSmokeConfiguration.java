package be.fanotmz.docsearch.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "docsearch.smoke.enabled", havingValue = "true")
public class AiSmokeConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiSmokeConfiguration.class);

    @Bean
    ApplicationRunner aiSmoke(ChatClient.Builder builder, EmbeddingModel embeddingModel) {
        return args -> {
            float[] vector = embeddingModel.embed("Document search smoke test.");
            if (vector.length == 0) {
                throw new IllegalStateException("Embedding model returned an empty vector");
            }
            for (float value : vector) {
                if (!Float.isFinite(value)) {
                    throw new IllegalStateException("Embedding model returned a non-finite value");
                }
            }
            LOGGER.info("Embedding smoke passed: dimensions={}", vector.length);
            String answer = builder.build().prompt()
                    .user("Reply with exactly DOCSEARCH_OK, with no explanation.")
                    .call().content();
            if (answer == null || !"DOCSEARCH_OK".equals(answer.strip())) {
                throw new IllegalStateException("Chat smoke did not return the expected marker");
            }
            LOGGER.info("Chat smoke passed");
        };
    }
}
