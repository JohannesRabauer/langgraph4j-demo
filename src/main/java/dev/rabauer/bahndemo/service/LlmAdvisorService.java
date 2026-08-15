package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Active only when bahn.advisor.enabled=true (docker-compose.yml sets this; spring-ai-starter-model-ollama
 * autoconfigures the ChatClient.Builder this depends on from spring.ai.ollama.* properties).
 * RuleBasedAdvisorService is always registered too (both as the fallback delegate this class uses, and
 * as the sole AdvisorService when the LLM path is disabled) - @Primary is what lets AdvisorNode's single
 * AdvisorService injection point resolve to this bean instead of failing on the ambiguity when both exist.
 *
 * Falls back to a rule-based recommendation if the Ollama call fails for any reason (model still being
 * pulled, container not warmed up yet, connection refused) so a slow/unready LLM backend can't break
 * the graph run.
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "bahn.advisor", name = "enabled", havingValue = "true")
public class LlmAdvisorService implements AdvisorService {

    private static final Logger log = LoggerFactory.getLogger(LlmAdvisorService.class);

    private final ChatClient chatClient;
    private final RuleBasedAdvisorService fallback;

    public LlmAdvisorService(ChatClient.Builder chatClientBuilder, RuleBasedAdvisorService fallback) {
        this.chatClient = chatClientBuilder.build();
        this.fallback = fallback;
    }

    @Override
    public String recommend(int delaySeconds, List<JourneyDto> alternatives) {
        if (alternatives.isEmpty()) {
            return fallback.recommend(delaySeconds, alternatives);
        }
        try {
            String prompt = buildPrompt(delaySeconds, alternatives);
            String response = chatClient.prompt(prompt).call().content();
            return response == null || response.isBlank() ? fallback.recommend(delaySeconds, alternatives) : response.trim();
        } catch (Exception e) {
            log.warn("Ollama advisor call failed ({}), falling back to rule-based recommendation", e.toString());
            return fallback.recommend(delaySeconds, alternatives);
        }
    }

    private String buildPrompt(int delaySeconds, List<JourneyDto> alternatives) {
        StringBuilder sb = new StringBuilder();
        sb.append("A train connection is delayed by ").append(delaySeconds)
                .append(" seconds. Here are the available alternative connections:\n");
        for (int i = 0; i < alternatives.size(); i++) {
            JourneyDto journey = alternatives.get(i);
            LegDto first = journey.firstLeg();
            LegDto last = journey.lastLeg();
            sb.append(i).append(": ");
            if (first != null && first.line() != null) {
                sb.append(first.line().name()).append(", ");
            }
            if (first != null) {
                sb.append("departs ").append(first.plannedDeparture()).append(", ");
            }
            if (last != null) {
                sb.append("arrives ").append(last.plannedArrival());
            }
            sb.append("\n");
        }
        sb.append("In two short sentences, recommend the best alternative and briefly explain why.");
        return sb.toString();
    }
}
