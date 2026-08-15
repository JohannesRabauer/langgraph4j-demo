package dev.rabauer.bahndemo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A single journey (one or more legs) as returned by v6.db.transport.rest.
 * {@code refreshToken} is what /journeys/{refreshToken} needs to re-fetch realtime data for
 * this exact journey later.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JourneyDto(String refreshToken, List<LegDto> legs) {

    public LegDto firstLeg() {
        return legs == null || legs.isEmpty() ? null : legs.get(0);
    }

    public LegDto lastLeg() {
        return legs == null || legs.isEmpty() ? null : legs.get(legs.size() - 1);
    }
}
