package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.util.TimeFormat;
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
 * Uses Spring AI's structured-output support (entity(...)) to get a concrete recommendedIndex back from the
 * model instead of parsing free text. Deliberately does NOT use entity(...)'s schema-validation/retry option
 * (spec.validateSchema()) - it was observed to hang indefinitely against Ollama/llama3.2 after a single
 * validation failure instead of retrying, silently stalling the workflow executor thread. Falls back to the
 * rule-based recommendation instead if the Ollama call fails, the parsed response is missing/blank, or the
 * index is out of bounds - so a slow/unready LLM backend or a small model's malformed JSON can't break the
 * graph run.
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
    public AdvisorRecommendation recommend(int delaySeconds, List<JourneyDto> alternatives) {
        if (alternatives.isEmpty()) {
            return fallback.recommend(delaySeconds, alternatives);
        }
        try {
            String prompt = buildPrompt(delaySeconds, alternatives);
            AdvisorRecommendation result = chatClient.prompt(prompt).call().entity(AdvisorRecommendation.class);
            if (result == null || result.recommendedIndex() == null
                    || result.recommendedIndex() < 0 || result.recommendedIndex() >= alternatives.size()
                    || result.rationale() == null || result.rationale().isBlank()) {
                log.warn("Ollama advisor returned an unusable recommendation ({}), falling back to rule-based recommendation", result);
                return fallback.recommend(delaySeconds, alternatives);
            }
            return result;
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
                sb.append("departs ").append(TimeFormat.format(first.plannedDeparture())).append(", ");
            }
            if (last != null) {
                sb.append("arrives ").append(TimeFormat.format(last.plannedArrival()));
            }
            sb.append("\n");
        }
        sb.append("Recommend the best alternative by its index and briefly explain why in one or two sentences.");
        return sb.toString();
    }
}
