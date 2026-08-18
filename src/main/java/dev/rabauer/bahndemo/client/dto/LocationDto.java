package dev.rabauer.bahndemo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * A station/stop/address - DbApiClient's internal model, built from api.transitous.org (MOTIS)
 * geocode results and itinerary legs (both nested inside journeys as origin/destination).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationDto(
        String type,
        String id,
        String name,
        Coordinates location) implements Serializable {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coordinates(Double latitude, Double longitude) implements Serializable {
    }

    @Override
    public String toString() {
        return name != null ? name : id;
    }
}
