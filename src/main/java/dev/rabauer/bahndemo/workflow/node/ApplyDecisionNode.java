package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Finalizes the workflow: sets "outcome" based on state.humanDecision(). This is the last node -
 * WorkflowOrchestrationService treats a present "outcome" as the signal that the graph run has
 * completed rather than merely paused.
 *
 * TODO(stream): implement the branching per HumanDecision and produce a user-facing outcome message:
 *   - ACCEPT_SUGGESTED: apply state.advisorRecommendedIndex()
 *   - PICK_ALTERNATIVE: apply state.selectedAlternativeIndex()
 *   - KEEP_WAITING: no alternative applied, just describe that monitoring continues
 * (the first two end up doing the same thing against a different index source - consider one shared
 * helper).
 */
@Component
public class ApplyDecisionNode implements NodeAction<DelayWorkflowState> {

    @Override
    public Map<String, Object> apply(DelayWorkflowState state) throws Exception {
        return Map.of();
    }
}
