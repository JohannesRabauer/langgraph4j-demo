package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.util.TimeFormat;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import dev.rabauer.bahndemo.workflow.HumanDecision;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Finalizes the workflow: sets "outcome" based on state.humanDecision(). ACCEPT_SUGGESTED and
 * PICK_ALTERNATIVE both resolve to a specific alternatives() index - the advisor's recommendedIndex for
 * the former, the human's selectedAlternativeIndex for the latter - and are applied identically. This is
 * the last node - WorkflowOrchestrationService treats a present "outcome" as the signal that the graph
 * run has completed rather than merely paused.
 */
@Component
public class ApplyDecisionNode implements NodeAction<DelayWorkflowState> {

    @Override
    public Map<String, Object> apply(DelayWorkflowState state) throws Exception {
        HumanDecision decision = state.humanDecision()
                .orElseThrow(() -> new IllegalStateException("applyDecision reached without a human decision"));

        String outcome = switch (decision) {
            case ACCEPT_SUGGESTED -> applyIndexChoice(state, state.advisorRecommendedIndex().orElse(null),
                    "No specific alternative was recommended; kept the original connection.");
            case PICK_ALTERNATIVE -> applyIndexChoice(state, state.selectedAlternativeIndex().orElse(null),
                    "Requested a specific alternative, but no valid selection was recorded; kept the original connection.");
            case KEEP_WAITING -> "Kept the original connection; monitoring continues.";
        };

        return Map.of("outcome", outcome, "log", List.of("applyDecision: " + outcome));
    }

    private String applyIndexChoice(DelayWorkflowState state, Integer index, String notFoundMessage) {
        List<JourneyDto> alternatives = state.alternatives();
        if (index == null || index < 0 || index >= alternatives.size()) {
            return notFoundMessage;
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
        return "%s, departing %s, arriving %s"
                .formatted(line, TimeFormat.format(firstLeg.plannedDeparture()), TimeFormat.format(lastLeg.plannedArrival()));
    }
}
