package be.fanotmz.docsearch;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class DeterministicChatTestConfiguration {
    @Bean
    @Primary
    DeterministicChatModel deterministicChatModel() {
        return new DeterministicChatModel();
    }

    public static final class DeterministicChatModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();
        private volatile String lastPrompt = "";

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            lastPrompt = prompt.getContents();
            String normalized = lastPrompt.toLowerCase(Locale.ROOT);
            if (normalized.contains("generation failure")) {
                throw new IllegalStateException("deterministic transport failure");
            }
            String output;
            if (normalized.contains("malformed model output")) {
                output = "not-json";
            }
            else if (normalized.contains("question:\nempty answer")) {
                output = "{\"status\":\"ANSWERED\",\"answer\":\"\"}";
            }
            else if (normalized.contains("uncited answer")) {
                output = "{\"status\":\"ANSWERED\",\"answer\":\"An unsupported answer.\"}";
            }
            else if (normalized.contains("fabricated citation")) {
                output = "{\"status\":\"ANSWERED\",\"answer\":\"A claim [S99]\"}";
            }
            else if (normalized.contains("model abstain")) {
                output = "{\"status\":\"INSUFFICIENT_EVIDENCE\",\"answer\":\"The model cannot determine this.\"}";
            }
            else if (normalized.contains("multiple citations")) {
                output = "{\"status\":\"ANSWERED\",\"answer\":\"Combined evidence [S2] and [S1], also [S1].\"}";
            }
            else {
                output = "{\"status\":\"ANSWERED\",\"answer\":\"The supplied evidence supports this answer. [S1]\"}";
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(output))));
        }

        public int callCount() {
            return calls.get();
        }

        public String lastPrompt() {
            return lastPrompt;
        }

        public void reset() {
            calls.set(0);
            lastPrompt = "";
        }
    }
}
