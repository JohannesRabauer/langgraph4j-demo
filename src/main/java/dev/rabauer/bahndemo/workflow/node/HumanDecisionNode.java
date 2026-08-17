package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Graph execution halts BEFORE this node (see DelayWorkflowConfig's interruptBefore("humanDecision")),
 * so by the time this actually runs, WorkflowOrchestrationService has already written "humanDecision"
 * (and optionally "selectedAlternativeIndex") into the state via CompiledGraph#updateState.
 *
 * TODO(stream): validate state.humanDecision() is present and append a log line describing the choice.
 */
@Component
public class HumanDecisionNode implements NodeAction<DelayWorkflowState> {

    @Override
    public Map<String, Object> apply(DelayWorkflowState state) throws Exception {
        return Map.of();
    }
}
