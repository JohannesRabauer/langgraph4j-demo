package dev.rabauer.bahndemo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Envelope returned by GET /journeys/{refreshToken}: {"journey": {...}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JourneyEnvelope(JourneyDto journey) {
}
