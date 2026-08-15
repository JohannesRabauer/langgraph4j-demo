package dev.rabauer.bahndemo.workflow;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * State threaded through the delay-handling graph (see DelayWorkflowConfig). Every node reads a
 * subset of these keys and returns a partial map of updates, which langgraph4j merges into a new
 * state snapshot.
 */
public class DelayWorkflowState extends AgentState {

    public DelayWorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> journeyId() {
        return value("journeyId");
    }

    public Optional<JourneyDto> originalJourney() {
        return value("originalJourney");
    }

    public Optional<Integer> delaySeconds() {
        return value("delaySeconds");
    }

    public List<JourneyDto> alternatives() {
        return this.<List<JourneyDto>>value("alternatives").orElseGet(List::of);
    }

    public Optional<String> advisorRecommendation() {
        return value("advisorRecommendation");
    }

    public Optional<HumanDecision> humanDecision() {
        return value("humanDecision");
    }

    public Optional<Integer> selectedAlternativeIndex() {
        return value("selectedAlternativeIndex");
    }

    /** Only ever set by ApplyDecisionNode - its presence means the graph run has completed. */
    public Optional<String> outcome() {
        return value("outcome");
    }

    public List<String> log() {
        return this.<List<String>>value("log").orElseGet(List::of);
    }
}
