package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.service.AdvisorRecommendation;
import dev.rabauer.bahndemo.service.AdvisorService;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Asks the active AdvisorService (rule-based by default, LLM-backed when bahn.advisor.enabled=true)
 * to recommend the best alternative given the delay and the alternatives found by AnalyzeDelayNode.
 */
@Component
public class AdvisorNode implements NodeAction<DelayWorkflowState> {

    private final AdvisorService advisorService;

    public AdvisorNode(AdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @Override
    public Map<String, Object> apply(DelayWorkflowState state) throws Exception {
        AdvisorRecommendation recommendation =
                advisorService.recommend(state.delaySeconds().orElse(0), state.alternatives());

        Map<String, Object> updates = new HashMap<>();
        updates.put("advisorRecommendation", recommendation.rationale());
        if (recommendation.recommendedIndex() != null) {
            updates.put("advisorRecommendedIndex", recommendation.recommendedIndex());
        }
        updates.put("log", List.of("advisor: " + recommendation.rationale()));
        return updates;
    }
}
