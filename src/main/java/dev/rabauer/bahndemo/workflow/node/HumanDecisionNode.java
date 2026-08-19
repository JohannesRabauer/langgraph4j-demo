package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.workflow.DelayWorkflowState;

import java.util.Map;

/**
 * Third graph node: validates that a human decision is present in the state before proceeding.
 *
 * The graph is compiled with {@code interruptBefore("humanDecision")}, so when execution reaches
 * this node the graph pauses and waits for the operator to inject the decision via
 * {@code graph.stream(GraphInput.resume(updatedState), config)}. This node itself just asserts the
 * decision is there (a safety net) and passes through without adding any new state keys.
 */
public class HumanDecisionNode {

    public Map<String, Object> apply(DelayWorkflowState state) {
        if (state.humanDecision().isEmpty()) {
            throw new IllegalStateException(
                    "humanDecision node reached without a decision in state for journey "
                    + state.journeyId().orElse("unknown")
                    + " - the interruptBefore should have paused the graph before reaching here.");
        }
        return Map.of(
                DelayWorkflowState.LOG, "Human decision: " + state.humanDecision().get()
        );
    }
}
