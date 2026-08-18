package dev.rabauer.bahndemo.workflow;

import dev.rabauer.bahndemo.client.dto.JourneyDto;

import java.util.List;
import java.util.Optional;

/**
 * A snapshot of the delay-handling workflow for one journey, pushed to the UI after each step.
 *
 * Plain, hand-rolled state - no checkpointing, no formal graph. It's the primitive stand-in for
 * what becomes a langgraph4j-managed graph state during the stream (see WorkflowOrchestrationService's
 * Javadoc). Kept immutable via toBuilder() so each published snapshot is a distinct value, mirroring
 * how a real graph state update produces a new state rather than mutating one in place.
 */
public final class DelayWorkflowState {

    private final String journeyId;
    private final JourneyDto originalJourney;
    private final Integer delaySeconds;
    private final List<JourneyDto> alternatives;
    private final String advisorRecommendation;
    private final Integer advisorRecommendedIndex;
    private final HumanDecision humanDecision;
    private final Integer selectedAlternativeIndex;
    private final String outcome;
    private final List<String> log;

    private DelayWorkflowState(Builder builder) {
        this.journeyId = builder.journeyId;
        this.originalJourney = builder.originalJourney;
        this.delaySeconds = builder.delaySeconds;
        this.alternatives = builder.alternatives;
        this.advisorRecommendation = builder.advisorRecommendation;
        this.advisorRecommendedIndex = builder.advisorRecommendedIndex;
        this.humanDecision = builder.humanDecision;
        this.selectedAlternativeIndex = builder.selectedAlternativeIndex;
        this.outcome = builder.outcome;
        this.log = builder.log;
    }

    public Optional<String> journeyId() {
        return Optional.ofNullable(journeyId);
    }

    public Optional<JourneyDto> originalJourney() {
        return Optional.ofNullable(originalJourney);
    }

    public Optional<Integer> delaySeconds() {
        return Optional.ofNullable(delaySeconds);
    }

    public List<JourneyDto> alternatives() {
        return alternatives;
    }

    public Optional<String> advisorRecommendation() {
        return Optional.ofNullable(advisorRecommendation);
    }

    public Optional<Integer> advisorRecommendedIndex() {
        return Optional.ofNullable(advisorRecommendedIndex);
    }

    public Optional<HumanDecision> humanDecision() {
        return Optional.ofNullable(humanDecision);
    }

    public Optional<Integer> selectedAlternativeIndex() {
        return Optional.ofNullable(selectedAlternativeIndex);
    }

    /** Only ever set once a decision has been applied - its presence means the run has completed. */
    public Optional<String> outcome() {
        return Optional.ofNullable(outcome);
    }

    public List<String> log() {
        return log;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.journeyId = journeyId;
        builder.originalJourney = originalJourney;
        builder.delaySeconds = delaySeconds;
        builder.alternatives = alternatives;
        builder.advisorRecommendation = advisorRecommendation;
        builder.advisorRecommendedIndex = advisorRecommendedIndex;
        builder.humanDecision = humanDecision;
        builder.selectedAlternativeIndex = selectedAlternativeIndex;
        builder.outcome = outcome;
        builder.log = log;
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String journeyId;
        private JourneyDto originalJourney;
        private Integer delaySeconds;
        private List<JourneyDto> alternatives = List.of();
        private String advisorRecommendation;
        private Integer advisorRecommendedIndex;
        private HumanDecision humanDecision;
        private Integer selectedAlternativeIndex;
        private String outcome;
        private List<String> log = List.of();

        public Builder journeyId(String journeyId) {
            this.journeyId = journeyId;
            return this;
        }

        public Builder originalJourney(JourneyDto originalJourney) {
            this.originalJourney = originalJourney;
            return this;
        }

        public Builder delaySeconds(Integer delaySeconds) {
            this.delaySeconds = delaySeconds;
            return this;
        }

        public Builder alternatives(List<JourneyDto> alternatives) {
            this.alternatives = alternatives;
            return this;
        }

        public Builder advisorRecommendation(String advisorRecommendation) {
            this.advisorRecommendation = advisorRecommendation;
            return this;
        }

        public Builder advisorRecommendedIndex(Integer advisorRecommendedIndex) {
            this.advisorRecommendedIndex = advisorRecommendedIndex;
            return this;
        }

        public Builder humanDecision(HumanDecision humanDecision) {
            this.humanDecision = humanDecision;
            return this;
        }

        public Builder selectedAlternativeIndex(Integer selectedAlternativeIndex) {
            this.selectedAlternativeIndex = selectedAlternativeIndex;
            return this;
        }

        public Builder outcome(String outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder log(List<String> log) {
            this.log = log;
            return this;
        }

        public DelayWorkflowState build() {
            return new DelayWorkflowState(this);
        }
    }
}
