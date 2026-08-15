package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import dev.rabauer.bahndemo.workflow.HumanDecision;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Finalizes the workflow: sets "outcome" based on state.humanDecision() (and, for PICK_ALTERNATIVE,
 * state.selectedAlternativeIndex()). This is the last node - WorkflowOrchestrationService treats a
 * present "outcome" as the signal that the graph run has completed rather than merely paused.
 */
@Component
public class ApplyDecisionNode implements NodeAction<DelayWorkflowState> {

    @Override
    public Map<String, Object> apply(DelayWorkflowState state) throws Exception {
        HumanDecision decision = state.humanDecision()
                .orElseThrow(() -> new IllegalStateException("applyDecision reached without a human decision"));

        String outcome = switch (decision) {
            case ACCEPT_SUGGESTED -> "Switched connection based on advisor recommendation: "
                    + state.advisorRecommendation().orElse("(no recommendation available)");
            case PICK_ALTERNATIVE -> applyAlternativeChoice(state);
            case KEEP_WAITING -> "Kept the original connection; monitoring continues.";
        };

        return Map.of("outcome", outcome, "log", List.of("applyDecision: " + outcome));
    }

    private String applyAlternativeChoice(DelayWorkflowState state) {
        List<JourneyDto> alternatives = state.alternatives();
        Integer index = state.selectedAlternativeIndex().orElse(null);
        if (index == null || index < 0 || index >= alternatives.size()) {
            return "Requested a specific alternative, but no valid selection was recorded; kept the original connection.";
        }
        return "Switched to alternative connection: " + describe(alternatives.get(index));
    }

    private String describe(JourneyDto journey) {
        LegDto firstLeg = journey.firstLeg();
        LegDto lastLeg = journey.lastLeg();
        if (firstLeg == null || lastLeg == null) {
            return journey.refreshToken();
        }
        String line = firstLeg.line() != null ? firstLeg.line().name() : "unknown line";
        return "%s, departing %s, arriving %s".formatted(line, firstLeg.plannedDeparture(), lastLeg.plannedArrival());
    }
}
