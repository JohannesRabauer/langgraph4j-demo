package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import dev.rabauer.bahndemo.workflow.HumanDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * The Vaadin-facing bridge for the delay-handling workflow.
 *
 * This is a hand-rolled stand-in for langgraph4j - the one thing meant to be built live. It runs the
 * same steps a graph would (analyze -> advise -> [pause for a human decision] -> apply decision) as
 * plain sequential method calls, and keeps the "paused" state in {@code pausedByJourneyId} instead of
 * a real checkpointer. During the stream this class (plus workflow.DelayWorkflowConfig and
 * workflow.node.*, both removed for now) gets rebuilt on top of langgraph4j's StateGraph,
 * CompiledGraph, and interruptBefore/resume - trading this class's bespoke ConcurrentHashMap for a
 * durable, resumable, framework-managed checkpoint per journey.
 *
 * Execution always runs on workflowExecutor, never the Vaadin UI thread. UI updates are delivered
 * through MonitoredJourney#getUiCallback(), which the UI wraps in UI.access(...) for server push -
 * this class never touches Vaadin's UI class directly.
 */
@Service
public class WorkflowOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrationService.class);

    private final DbApiClient dbApiClient;
    private final AdvisorService advisorService;
    private final MonitoredJourneyRegistry registry;
    private final ExecutorService workflowExecutor;

    private final Map<String, DelayWorkflowState> pausedByJourneyId = new ConcurrentHashMap<>();

    public WorkflowOrchestrationService(DbApiClient dbApiClient,
                                         AdvisorService advisorService,
                                         MonitoredJourneyRegistry registry,
                                         ExecutorService workflowExecutor) {
        this.dbApiClient = dbApiClient;
        this.advisorService = advisorService;
        this.registry = registry;
        this.workflowExecutor = workflowExecutor;
    }

    /** Starts a fresh run for the given journey: find alternatives, ask the advisor, then pause for a decision. */
    public void startWorkflow(MonitoredJourney journey) {
        workflowExecutor.submit(() -> {
            try {
                List<String> steps = new ArrayList<>();

                List<JourneyDto> alternatives = findAlternatives(journey);
                steps.add("Found %d alternative connection(s)".formatted(alternatives.size()));

                AdvisorRecommendation recommendation = advisorService.recommend(journey.getLastDelaySeconds(), alternatives);
                steps.add("Advisor: " + recommendation.rationale());

                DelayWorkflowState state = DelayWorkflowState.builder()
                        .journeyId(journey.getId())
                        .originalJourney(journey.getJourney())
                        .delaySeconds(journey.getLastDelaySeconds())
                        .alternatives(alternatives)
                        .advisorRecommendation(recommendation.rationale())
                        .advisorRecommendedIndex(recommendation.recommendedIndex())
                        .log(steps)
                        .build();

                pausedByJourneyId.put(journey.getId(), state);
                publish(journey, state);
            } catch (Exception e) {
                log.warn("Workflow start failed for journey {}: {}", journey.getId(), e.toString());
            }
        });
    }

    /** Resumes a paused run with the human's decision and publishes the final outcome. */
    public void resumeWithDecision(String journeyId, HumanDecision decision, Integer selectedAlternativeIndex) {
        registry.find(journeyId).ifPresent(journey -> workflowExecutor.submit(() -> {
            try {
                DelayWorkflowState paused = pausedByJourneyId.remove(journeyId);
                if (paused == null) {
                    log.warn("No paused workflow found for journey {}", journeyId);
                    return;
                }

                List<String> steps = new ArrayList<>(paused.log());
                steps.add("Human decision: " + decision);

                String outcome = describeOutcome(paused, decision, selectedAlternativeIndex);

                DelayWorkflowState finalState = paused.toBuilder()
                        .humanDecision(decision)
                        .selectedAlternativeIndex(selectedAlternativeIndex)
                        .outcome(outcome)
                        .log(steps)
                        .build();

                journey.setWorkflowActive(false);
                publish(journey, finalState);
            } catch (Exception e) {
                log.warn("Workflow resume failed for journey {}: {}", journeyId, e.toString());
            }
        }));
    }

    private List<JourneyDto> findAlternatives(MonitoredJourney journey) {
        JourneyDto original = journey.getJourney();
        LegDto firstLeg = original.firstLeg();
        LegDto lastLeg = original.lastLeg();
        if (firstLeg == null || lastLeg == null || firstLeg.origin() == null || lastLeg.destination() == null) {
            return List.of();
        }
        return dbApiClient.searchJourneys(firstLeg.origin().id(), lastLeg.destination().id(), Instant.now()).stream()
                .filter(candidate -> !candidate.refreshToken().equals(original.refreshToken()))
                .toList();
    }

    /** ACCEPT_SUGGESTED and PICK_ALTERNATIVE both resolve an alternatives() index, just from a different source. */
    private String describeOutcome(DelayWorkflowState state, HumanDecision decision, Integer selectedAlternativeIndex) {
        return switch (decision) {
            case ACCEPT_SUGGESTED -> describeSwitch(state, state.advisorRecommendedIndex().orElse(null));
            case PICK_ALTERNATIVE -> describeSwitch(state, selectedAlternativeIndex);
            case KEEP_WAITING -> "Kept the original connection; monitoring continues.";
        };
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

    private void publish(MonitoredJourney journey, DelayWorkflowState state) {
        Consumer<DelayWorkflowState> callback = journey.getUiCallback();
        if (callback != null) {
            callback.accept(state);
        }
    }
}
