package dev.rabauer.bahndemo.client;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LocationDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

/**
 * Thin client for the public, unauthenticated v6.db.transport.rest API (backed by db-vendo-client).
 * Rate limit: 100 requests/minute - keep polling intervals well under that.
 *
 * TODO(stream): implement the actual HTTP calls below and confirm response shapes with e.g.
 *   curl "https://v6.db.transport.rest/locations?query=Berlin&results=5"
 *   curl "https://v6.db.transport.rest/journeys?from=<id>&to=<id>"
 * before wiring up refreshJourney's exact envelope (bare JourneyDto vs {"journey": {...}}).
 */
@Component
public class DbApiClient {

    private final RestClient restClient;

    public DbApiClient(RestClient dbApiRestClient) {
        this.restClient = dbApiRestClient;
    }

    /** GET /locations?query=... - station/address autocomplete. */
    public List<LocationDto> searchLocations(String query) {
        // TODO: restClient.get().uri(uriBuilder -> uriBuilder.path("/locations")
        //   .queryParam("query", query).queryParam("results", 5).build())
        //   .retrieve().body(LocationDto[].class)
        return List.of();
    }

    /** GET /journeys?from=&to=&departure= - connection search between two stations. */
    public List<JourneyDto> searchJourneys(String fromId, String toId, Instant when) {
        // TODO: restClient.get().uri(...).retrieve().body(JourneysResponse.class).journeys()
        return List.of();
    }

    /** GET /journeys/{refreshToken} - re-fetch realtime data (delays) for a previously returned journey. */
    public JourneyDto refreshJourney(String refreshToken) {
        // TODO: verify response envelope live before implementing
        return null;
    }
}
