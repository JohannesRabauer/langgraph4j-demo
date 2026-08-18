package dev.rabauer.bahndemo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Envelope returned by GET /journeys: {"journeys": [...]}.
 *
 * TODO(stream): GET /journeys/{refreshToken} may return a bare JourneyDto instead of this
 * envelope - verify live and adjust DbApiClient#refreshJourney's deserialization target accordingly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JourneysResponse(List<JourneyDto> journeys) {
}
