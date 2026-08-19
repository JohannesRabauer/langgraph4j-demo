package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.service.AdvisorRecommendation;
import dev.rabauer.bahndemo.service.AdvisorService;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;

import java.util.Map;

import static dev.rabauer.bahndemo.workflow.DelayWorkflowState.*;

/**
 * Second graph node: calls the advisor (rule-based or LLM) to recommend one of the alternatives.
 * Produces {@code advisorRecommendation}, {@code advisorRecommendedIndex}, and a {@code log} entry.
 */
public class AdvisorNode {

    private final AdvisorService advisorService;

    public AdvisorNode(AdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    public Map<String, Object> apply(DelayWorkflowState state) {
        int delaySeconds = state.delaySeconds().orElse(0);
        AdvisorRecommendation rec = advisorService.recommend(delaySeconds, state.alternatives());
        return Map.of(
                ADVISOR_RECOMMENDATION, rec.rationale(),
                ADVISOR_RECOMMENDED_INDEX, rec.recommendedIndex(),
                LOG, "Advisor: " + rec.rationale()
        );
    }
}
