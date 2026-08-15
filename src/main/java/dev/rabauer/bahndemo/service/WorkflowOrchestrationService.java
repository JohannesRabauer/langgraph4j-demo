package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import dev.rabauer.bahndemo.workflow.HumanDecision;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * The Vaadin &lt;-&gt; langgraph4j bridge: the architectural core of the demo.
 *
 * Graph execution always runs on workflowExecutor, never the Vaadin UI thread. UI updates are
 * delivered back through MonitoredJourney#getUiCallback(), which the UI wraps in UI.access(...) for
 * server push - this class never touches Vaadin's UI class directly.
 *
 * Pause vs. done is detected from our own state shape (state.outcome() empty vs. present), not from
 * an unverified "next node" API: "outcome" is only ever set by ApplyDecisionNode, the graph's last node.
 */
@Service
public class WorkflowOrchestrationService {

    private final CompiledGraph<DelayWorkflowState> graph;
    private final ExecutorService workflowExecutor;
    private final MonitoredJourneyRegistry registry;

    public WorkflowOrchestrationService(CompiledGraph<DelayWorkflowState> graph,
                                         ExecutorService workflowExecutor,
                                         MonitoredJourneyRegistry registry) {
        this.graph = graph;
        this.workflowExecutor = workflowExecutor;
        this.registry = registry;
    }

    /** Starts a fresh graph run for the given journey, keyed by journey.getId() as the threadId. */
    public void startWorkflow(MonitoredJourney journey) {
        RunnableConfig config = RunnableConfig.builder().threadId(journey.getId()).build();

        Map<String, Object> initial = new HashMap<>();
        initial.put("journeyId", journey.getId());
        initial.put("originalJourney", journey.getJourney());
        initial.put("delaySeconds", journey.getLastDelaySeconds());

        workflowExecutor.submit(() -> {
            try {
                for (var ignored : graph.stream(GraphInput.args(initial), config)) {
                    // Intentionally empty: the stream halts on its own right before "humanDecision"
                    // because of CompileConfig#interruptBefore in DelayWorkflowConfig.
                }
                publishSnapshot(journey, config);
            } catch (Exception e) {
                // TODO(stream): log and surface the error via journey.getUiCallback()
            }
        });
    }

    /** Resumes a paused graph run with the human's decision, then drains it to completion. */
    public void resumeWithDecision(String journeyId, HumanDecision decision, Integer selectedAlternativeIndex) {
        registry.find(journeyId).ifPresent(journey -> workflowExecutor.submit(() -> {
            try {
                RunnableConfig config = RunnableConfig.builder().threadId(journeyId).build();

                Map<String, Object> update = new HashMap<>();
                update.put("humanDecision", decision);
                if (selectedAlternativeIndex != null) {
                    update.put("selectedAlternativeIndex", selectedAlternativeIndex);
                }
                graph.updateState(config, update, null);

                for (var ignored : graph.stream(GraphInput.resume(), config)) {
                    // drain to completion: humanDecision -> applyDecision -> END
                }
                journey.setWorkflowActive(false);
                publishSnapshot(journey, config);
            } catch (Exception e) {
                // TODO(stream): log and surface the error via journey.getUiCallback()
            }
        }));
    }

    private void publishSnapshot(MonitoredJourney journey, RunnableConfig config) {
        StateSnapshot<DelayWorkflowState> snapshot = graph.getState(config);
        Consumer<DelayWorkflowState> callback = journey.getUiCallback();
        if (callback != null) {
            callback.accept(snapshot.state());
        }
    }
}
