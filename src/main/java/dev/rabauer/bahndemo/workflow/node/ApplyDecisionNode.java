package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Finalizes the workflow: sets "outcome" based on state.humanDecision() (and, for PICK_ALTERNATIVE,
 * state.selectedAlternativeIndex()). This is the last node - WorkflowOrchestrationService treats a
 * present "outcome" as the signal that the graph run has completed rather than merely paused.
 *
 * TODO(stream): implement the branching per HumanDecision and produce a user-facing outcome message.
 */
@Component
public class ApplyDecisionNode implements NodeAction<DelayWorkflowState> {

    @Override
    public Map<String, Object> apply(DelayWorkflowState state) throws Exception {
        return Map.of();
    }
}
