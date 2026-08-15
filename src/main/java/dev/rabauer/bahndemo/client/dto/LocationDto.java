package dev.rabauer.bahndemo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A station/stop/address as returned by v6.db.transport.rest's /locations endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationDto(
        String type,
        String id,
        String name,
        Double latitude,
        Double longitude) {

    @Override
    public String toString() {
        return name != null ? name : id;
    }
}
