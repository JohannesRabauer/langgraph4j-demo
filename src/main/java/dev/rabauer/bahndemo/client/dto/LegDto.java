package dev.rabauer.bahndemo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.Instant;

/**
 * One leg of a journey - DbApiClient's internal model, built from api.transitous.org (MOTIS)
 * responses. {@code departureDelay}/{@code arrivalDelay} are in seconds, computed from that API's
 * scheduled vs. realtime times (null when there's no delay or no realtime data). MOTIS returns many
 * more fields (polyline, intermediate stops, fares, ...) which are intentionally ignored here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LegDto(
        LocationDto origin,
        LocationDto destination,
        Instant departure,
        Instant plannedDeparture,
        Integer departureDelay,
        Instant arrival,
        Instant plannedArrival,
        Integer arrivalDelay,
        LineDto line,
        boolean cancelled,
        boolean walking) implements Serializable {
}
