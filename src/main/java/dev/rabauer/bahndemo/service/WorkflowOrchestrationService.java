package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import dev.rabauer.bahndemo.workflow.HumanDecision;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static dev.rabauer.bahndemo.workflow.DelayWorkflowState.*;

/**
 * The Vaadin-facing bridge for the delay-handling workflow.
 *
 * Drives a langgraph4j {@link CompiledGraph} compiled with
 * {@code interruptBefore("humanDecision")}. Each journey maps to its own graph thread via a
 * {@link RunnableConfig} whose {@code threadId} is the journey ID - that's the single correlation
 * key that lets a browser click resume the exact right paused graph run.
 *
 * <ul>
 *   <li>{@link #startWorkflow} submits the initial graph input and streams until the interrupt;
 *       after {@code analyzeDelay} and {@code advisor} run, the graph checkpoints and halts before
 *       {@code humanDecision}. The state at that point is pushed to the UI as the "paused, your
 *       decision is needed" snapshot.</li>
 *   <li>{@link #resumeWithDecision} injects the operator's decision into the checkpointed state via
 *       {@code GraphInput.resume(updatedState)} and streams the rest of the graph to completion.</li>
 * </ul>
 *
 * Execution always runs on {@code workflowExecutor}, never the Vaadin UI thread. UI updates are
 * delivered through {@link MonitoredJourney#getUiCallback()}, which the UI wraps in
 * {@code UI.access(...)} for server push.
 */
@Service
public class WorkflowOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrationService.class);

    private final CompiledGraph<DelayWorkflowState> graph;
    private final MonitoredJourneyRegistry registry;
    private final ExecutorService workflowExecutor;

    public WorkflowOrchestrationService(CompiledGraph<DelayWorkflowState> graph,
                                        MonitoredJourneyRegistry registry,
                                        ExecutorService workflowExecutor) {
        this.graph = graph;
        this.registry = registry;
        this.workflowExecutor = workflowExecutor;
    }

    /** Starts a fresh graph run for the given journey. Streams until the humanDecision interrupt. */
    public void startWorkflow(MonitoredJourney journey) {
        try {
            var config = runnableConfig(journey.getId());
            var input = GraphInput.args(Map.of(
                    JOURNEY_ID,        journey.getId(),
                    ORIGINAL_JOURNEY,  journey.getJourney(),
                    DELAY_SECONDS,     journey.getLastDelaySeconds()
            ));

            // Stream until interruptBefore("humanDecision") pauses the graph.
            graph.stream(input, config).forEachAsync(output ->
                    log.info("Graph node completed: {}", output.node()))
                    .thenApply(GraphResult::from)
                    .thenApply(result -> {
                        if(result.isInterruptionMetadata())
                        {
                            publish(
                                    journey,
                                    new DelayWorkflowState(result.asInterruptionMetadata().state().data())
                            );
                        }
                        return null;
                    });

        } catch (Exception e) {
            log.warn("Workflow start failed for journey {}: {}", journey.getId(), e.toString());
        }
    }

    /** Injects the human decision and resumes the paused graph run to completion. */
    public void resumeWithDecision(String journeyId, HumanDecision decision,
                                   Integer selectedAlternativeIndex) {
        registry.find(journeyId).ifPresent(journey -> {
            try {
                var config = runnableConfig(journeyId);

                // Inject the decision into the checkpointed state and resume.
                Map<String, Object> decisionUpdate = selectedAlternativeIndex != null
                        ? Map.of(HUMAN_DECISION, decision,
                                 SELECTED_ALTERNATIVE_INDEX, selectedAlternativeIndex)
                        : Map.of(HUMAN_DECISION, decision);

                graph.stream(GraphInput.resume(decisionUpdate), config)
                        .forEachAsync(output -> log.debug("Graph node completed: {}", output.node()))
                        .thenApply(GraphResult::from)
                        .thenApply(result -> {
                            if(result.isStateDataOrCheckpointSaverTag())
                            {
                                journey.setWorkflowActive(false);
                                publish(
                                        journey,
                                        new DelayWorkflowState(result.asStateDataOrLastCheckpointStateData())
                                );
                            }
                            return null;
                        });

            } catch (Exception e) {
                log.warn("Workflow resume failed for journey {}: {}", journeyId, e.toString());
            }
        });
    }

    private static RunnableConfig runnableConfig(String journeyId) {
        return RunnableConfig.builder().threadId(journeyId).build();
    }

    private void publish(MonitoredJourney journey, DelayWorkflowState state) {
        Consumer<DelayWorkflowState> callback = journey.getUiCallback();
        if (callback != null) {
            callback.accept(state);
        }
    }
}
