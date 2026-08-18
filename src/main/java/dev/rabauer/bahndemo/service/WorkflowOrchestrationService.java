package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import dev.rabauer.bahndemo.workflow.HumanDecision;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.utils.CollectionsUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static java.util.concurrent.CompletableFuture.failedFuture;

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
    private final MonitoredJourneyRegistry registry;

    public WorkflowOrchestrationService(CompiledGraph<DelayWorkflowState> graph,
                                         MonitoredJourneyRegistry registry) {
        this.graph = graph;
        this.registry = registry;
    }

    /** Starts a fresh graph run for the given journey, keyed by journey.getId() as the threadId. */
    public void startWorkflow(MonitoredJourney journey) {
        RunnableConfig config = RunnableConfig.builder().threadId(journey.getId()).build();

        Map<String, Object> initial = new HashMap<>();
        initial.put("journeyId", journey.getId());
        initial.put("originalJourney", journey.getJourney());
        initial.put("delaySeconds", journey.getLastDelaySeconds());

        try {
            System.out.printf("""
                    =================================
                    Starting workflow for journey %s
                    ================================
                    """,
                    journey.getId());
            graph.stream(GraphInput.args(initial), config).forEachAsync(step -> {
                // Intentionally empty: the stream halts on its own right before "humanDecision"
                // because of CompileConfig#interruptBefore in DelayWorkflowConfig.

                System.out.printf("""
                        Executed step: '%s' for journey '%s' on thread %s
                        """,
                        step.node(), journey.getId(), Thread.currentThread().getName());
            })
            .thenAccept($1 -> {
                final var result =  GraphResult.from($1);

                if( result.isInterruptionMetadata()) {

                    final InterruptionMetadata<DelayWorkflowState> interruptionMetadata =
                            result.asInterruptionMetadata();

                    System.out.printf("""
                            Workflow paused after node '%s' for journey %s with state:
                            %s
                            """,
                            interruptionMetadata.nodeId(),  journey.getId(), interruptionMetadata.state().toString());

                    publishSnapshot(journey, config, interruptionMetadata.state());

                }

            });

        } catch (Exception e) {
            // TODO(stream): log and surface the error via journey.getUiCallback()
        }
    }

    /** Resumes a paused graph run with the human's decision, then drains it to completion. */
    public CompletableFuture<GraphResult> resumeWithDecision(String journeyId, HumanDecision decision, Integer selectedAlternativeIndex) {
        final var optJourney = registry.find(journeyId);

        if( optJourney.isEmpty()) {
            return failedFuture(new IllegalArgumentException("No journey found for id %s ".formatted(journeyId)));
        }

        final var journey = optJourney.get();

        try {
            RunnableConfig config = RunnableConfig.builder().threadId(journeyId).build();

            Map<String, Object> update = new HashMap<>();
            update.put("humanDecision", decision);
            if (selectedAlternativeIndex != null) {
                update.put("selectedAlternativeIndex", selectedAlternativeIndex);
            }
            graph.updateState(config, update, null);

            System.out.printf("""
                =================================
                Resuming workflow for journey %s
                ================================
                """,
                    journey.getId());

            return graph.stream(GraphInput.resume(), config).forEachAsync(step -> {

                    System.out.printf("""
                    Step: %s
                    journey %s
                    Thread %s
                    """,
                    step.node(), journey.getId(), Thread.currentThread().getName());
            })
            .thenApply($1 -> {
                final var result = GraphResult.from($1);
                if( result.isStateDataOrCheckpointSaverTag()) {

                    final Map<String,Object> state = result.asStateDataOrLastCheckpointStateData();

                    System.out.printf("""
                        Workflow for journey %s terminated with state:
                        %s
                        """, journey.getId(), CollectionsUtils.toString(state));

                    publishSnapshot(journey, config, state);
                    journey.setWorkflowActive(false);
                    return result;
                }
                return GraphResult.empty();
            });
        } catch (Exception e) {
            // TODO(stream): log and surface the error via journey.getUiCallback()
            return failedFuture(e);
        }
        String originalTripIdentity = DbApiClient.tripIdentity(original.refreshToken());
        return dbApiClient.searchJourneys(firstLeg.origin().id(), lastLeg.destination().id(), Instant.now()).stream()
                .filter(candidate -> !DbApiClient.tripIdentity(candidate.refreshToken()).equals(originalTripIdentity))
                .toList();

    }

    private void publishSnapshot(MonitoredJourney journey, RunnableConfig config, DelayWorkflowState state) {
        Consumer<DelayWorkflowState> callback = journey.getUiCallback();
        if (callback != null) {
            callback.accept(state);
        }
    }
    private void publishSnapshot(MonitoredJourney journey, RunnableConfig config, Map<String,Object> state) {
        publishSnapshot(journey, config, graph.stateGraph.getStateFactory().apply(state));
    }
}
