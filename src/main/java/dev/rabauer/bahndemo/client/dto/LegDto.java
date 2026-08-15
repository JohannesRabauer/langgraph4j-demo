package dev.rabauer.bahndemo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.time.Instant;

/**
 * One leg of a journey. {@code departureDelay}/{@code arrivalDelay} are in seconds, as returned
 * by v6.db.transport.rest (null when no realtime data is available). The actual API returns many
 * more fields (remarks, polyline, price, ...) which are intentionally ignored here.
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
