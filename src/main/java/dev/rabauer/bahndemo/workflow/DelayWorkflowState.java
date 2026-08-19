package dev.rabauer.bahndemo.workflow;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Langgraph4j graph state for the delay-handling workflow.
 *
 * Extends {@link AgentState} so the compiled graph can checkpoint it via MemorySaver.
 * The {@link #SCHEMA} registers a list-appender channel for {@code "log"} so each node's
 * log line accumulates instead of overwriting, and plain base channels for every other key.
 *
 * Accessor signatures are kept identical to the original plain-class version so
 * {@code MonitoringPanel} needs no changes.
 */
public class DelayWorkflowState extends AgentState {

    // Keys stored in the backing map
    public static final String JOURNEY_ID = "journeyId";
    public static final String ORIGINAL_JOURNEY = "originalJourney";
    public static final String DELAY_SECONDS = "delaySeconds";
    public static final String ALTERNATIVES = "alternatives";
    public static final String ADVISOR_RECOMMENDATION = "advisorRecommendation";
    public static final String ADVISOR_RECOMMENDED_INDEX = "advisorRecommendedIndex";
    public static final String HUMAN_DECISION = "humanDecision";
    public static final String SELECTED_ALTERNATIVE_INDEX = "selectedAlternativeIndex";
    public static final String OUTCOME = "outcome";
    public static final String LOG = "log";

    /**
     * Channel schema.  The {@code "log"} key uses an appender so successive node updates
     * each add their entry to the accumulated list rather than replacing it.
     */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            LOG, Channels.appender(ArrayList::new)
    );

    /** Required by langgraph4j's AgentStateFactory contract. */
    public DelayWorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> journeyId() {
        return value(JOURNEY_ID);
    }

    public Optional<JourneyDto> originalJourney() {
        return value(ORIGINAL_JOURNEY);
    }

    public Optional<Integer> delaySeconds() {
        return value(DELAY_SECONDS);
    }

    @SuppressWarnings("unchecked")
    public List<JourneyDto> alternatives() {
        return this.<List<JourneyDto>>value(ALTERNATIVES).orElse(List.of());
    }

    public Optional<String> advisorRecommendation() {
        return value(ADVISOR_RECOMMENDATION);
    }

    public Optional<Integer> advisorRecommendedIndex() {
        return value(ADVISOR_RECOMMENDED_INDEX);
    }

    public Optional<HumanDecision> humanDecision() {
        return value(HUMAN_DECISION);
    }

    public Optional<Integer> selectedAlternativeIndex() {
        return value(SELECTED_ALTERNATIVE_INDEX);
    }

    /** Only set once a decision has been applied - its presence means the run has completed. */
    public Optional<String> outcome() {
        return value(OUTCOME);
    }

    @SuppressWarnings("unchecked")
    public List<String> log() {
        return this.<List<String>>value(LOG).orElse(List.of());
    }
}
