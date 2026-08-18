package dev.rabauer.bahndemo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;

/**
 * A single journey (one or more legs) as returned by v6.db.transport.rest.
 * {@code refreshToken} is what /journeys/{refreshToken} needs to re-fetch realtime data for
 * this exact journey later.
 *
 * Implements Serializable because langgraph4j's MemorySaver checkpointer serializes the whole
 * graph state (via ObjectOutputStream) on every step - any non-serializable value stored in
 * DelayWorkflowState breaks checkpointing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JourneyDto(String refreshToken, List<LegDto> legs) implements Serializable {

    public LegDto firstLeg() {
        return legs == null || legs.isEmpty() ? null : legs.get(0);
    }

    public LegDto lastLeg() {
        return legs == null || legs.isEmpty() ? null : legs.get(legs.size() - 1);
    }
}
