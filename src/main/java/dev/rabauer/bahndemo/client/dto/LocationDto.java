package dev.rabauer.bahndemo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * A station/stop/address as returned by v6.db.transport.rest's /locations endpoint (and nested
 * inside legs as origin/destination). Coordinates are nested under "location", not top-level.
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
