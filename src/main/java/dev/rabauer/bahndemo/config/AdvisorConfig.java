package dev.rabauer.bahndemo.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Only registers a ChatModel bean when bahn.advisor.enabled=true, so LlmAdvisorService (which
 * requires this bean) never activates - and this class's OPENAI_API_KEY lookup never runs -
 * unless the demo operator explicitly opts in.
 */
@Configuration
@ConditionalOnProperty(prefix = "bahn.advisor", name = "enabled", havingValue = "true")
public class AdvisorConfig {

    @Bean
    public ChatModel chatModel(
            @Value("${langchain4j.open-ai.api-key:}") String apiKey,
            @Value("${langchain4j.open-ai.model-name:gpt-4o-mini}") String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
}
