package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import dev.rabauer.bahndemo.workflow.HumanDecision;

import java.util.List;
import java.util.Map;

import static dev.rabauer.bahndemo.workflow.DelayWorkflowState.*;

/**
 * Fourth graph node: translates the human decision into a human-readable outcome string.
 * Produces {@code outcome} and a {@code log} entry, completing the graph run.
 */
public class ApplyDecisionNode {

    public Map<String, Object> apply(DelayWorkflowState state) {
        HumanDecision decision = state.humanDecision().orElseThrow(
                () -> new IllegalStateException("applyDecision reached without a humanDecision"));
        Integer selectedIndex = state.selectedAlternativeIndex().orElse(null);

        String outcome = switch (decision) {
            case ACCEPT_SUGGESTED -> describeSwitch(state, state.advisorRecommendedIndex().orElse(null));
            case PICK_ALTERNATIVE  -> describeSwitch(state, selectedIndex);
            case KEEP_WAITING      -> "Kept the original connection; monitoring continues.";
        };

        return Map.of(OUTCOME, outcome);
    }

    private String describeSwitch(DelayWorkflowState state, Integer index) {
        List<JourneyDto> alternatives = state.alternatives();
        if (index == null || index < 0 || index >= alternatives.size()) {
            return "No alternative was available to switch to; kept the original connection.";
        }
        LegDto firstLeg = alternatives.get(index).firstLeg();
        String line = firstLeg != null && firstLeg.line() != null ? firstLeg.line().name() : "the alternative";
        return "Switched to " + line + ".";
    }
}
