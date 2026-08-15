package dev.rabauer.bahndemo.client;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.JourneyEnvelope;
import dev.rabauer.bahndemo.client.dto.JourneysResponse;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.client.dto.LineDto;
import dev.rabauer.bahndemo.client.dto.LocationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Thin client for the public, unauthenticated v6.db.transport.rest API (backed by db-vendo-client).
 * Rate limit: 100 requests/minute - keep polling intervals well under that.
 *
 * Every call falls back to a small hardcoded offline dataset on failure, so the demo (search,
 * monitoring, delay simulation) keeps working even if the live API is unreachable or rate-limited -
 * a real condition observed while building this client, not a hypothetical one.
 */
@Component
public class DbApiClient {

    private static final Logger log = LoggerFactory.getLogger(DbApiClient.class);

    private static final LocationDto BERLIN_HBF =
            new LocationDto("station", "8011160", "Berlin Hbf", new LocationDto.Coordinates(52.525589, 13.369548));
    private static final LocationDto MUNICH_HBF =
            new LocationDto("station", "8000261", "München Hbf", new LocationDto.Coordinates(48.140228, 11.558339));
    private static final LocationDto HAMBURG_HBF =
            new LocationDto("station", "8002549", "Hamburg Hbf", new LocationDto.Coordinates(53.552736, 10.006909));

    private final RestClient restClient;

    public DbApiClient(RestClient dbApiRestClient) {
        this.restClient = dbApiRestClient;
    }

    /** GET /locations?query=... - station/address autocomplete. */
    public List<LocationDto> searchLocations(String query) {
        try {
            LocationDto[] result = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/locations")
                            .queryParam("query", query)
                            .queryParam("results", 5)
                            .build())
                    .retrieve()
                    .body(LocationDto[].class);
            return result == null ? List.of() : List.of(result);
        } catch (RestClientException e) {
            log.warn("v6.db.transport.rest /locations unavailable ({}), using offline fallback data", e.toString());
            return fallbackLocations(query);
        }
    }

    /** GET /journeys?from=&to=&departure= - connection search between two stations. */
    public List<JourneyDto> searchJourneys(String fromId, String toId, Instant when) {
        try {
            JourneysResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/journeys")
                            .queryParam("from", fromId)
                            .queryParam("to", toId)
                            .queryParam("departure", when)
                            .build())
                    .retrieve()
                    .body(JourneysResponse.class);
            return response == null || response.journeys() == null ? List.of() : response.journeys();
        } catch (RestClientException e) {
            log.warn("v6.db.transport.rest /journeys unavailable ({}), using offline fallback data", e.toString());
            return fallbackJourneys(fromId, toId, when);
        }
    }

    /** GET /journeys/{refreshToken} - re-fetch realtime data (delays) for a previously returned journey. */
    public JourneyDto refreshJourney(String refreshToken) {
        if (refreshToken != null && refreshToken.startsWith("offline-")) {
            return fallbackRefreshedJourney(refreshToken);
        }
        try {
            JourneyEnvelope envelope = restClient.get()
                    .uri("/journeys/{refreshToken}", refreshToken)
                    .retrieve()
                    .body(JourneyEnvelope.class);
            return envelope == null ? null : envelope.journey();
        } catch (RestClientException e) {
            log.warn("v6.db.transport.rest /journeys/{} unavailable ({})", refreshToken, e.toString());
            return null;
        }
    }

    private List<LocationDto> fallbackLocations(String query) {
        String needle = query == null ? "" : query.toLowerCase();
        return List.of(BERLIN_HBF, MUNICH_HBF, HAMBURG_HBF).stream()
                .filter(loc -> needle.isBlank() || loc.name().toLowerCase().contains(needle))
                .toList();
    }

    private List<JourneyDto> fallbackJourneys(String fromId, String toId, Instant when) {
        LocationDto origin = new LocationDto("station", fromId, fromId, null);
        LocationDto destination = new LocationDto("station", toId, toId, null);
        Instant baseDeparture = when != null ? when : Instant.now();

        JourneyDto fast = offlineJourney("offline-1", origin, destination, baseDeparture,
                Duration.ofMinutes(20), Duration.ofHours(4), "ICE 501", "nationalExpress", 0);
        JourneyDto slower = offlineJourney("offline-2", origin, destination, baseDeparture,
                Duration.ofMinutes(50), Duration.ofHours(5), "IC 2024", "national", 0);
        return List.of(fast, slower);
    }

    private JourneyDto fallbackRefreshedJourney(String refreshToken) {
        LocationDto origin = new LocationDto("station", "offline-origin", "Origin", null);
        LocationDto destination = new LocationDto("station", "offline-destination", "Destination", null);
        return offlineJourney(refreshToken, origin, destination, Instant.now(),
                Duration.ofMinutes(10), Duration.ofHours(3), "ICE 501", "nationalExpress", 0);
    }

    private JourneyDto offlineJourney(String refreshToken, LocationDto origin, LocationDto destination,
                                       Instant baseDeparture, Duration departureOffset, Duration travelTime,
                                       String lineName, String product, int delaySeconds) {
        Instant plannedDeparture = baseDeparture.plus(departureOffset);
        Instant plannedArrival = plannedDeparture.plus(travelTime);
        Instant actualDeparture = plannedDeparture.plusSeconds(delaySeconds);
        Instant actualArrival = plannedArrival.plusSeconds(delaySeconds);

        LegDto leg = new LegDto(
                origin, destination,
                actualDeparture, plannedDeparture, delaySeconds > 0 ? delaySeconds : null,
                actualArrival, plannedArrival, delaySeconds > 0 ? delaySeconds : null,
                new LineDto(lineName, product),
                false, false);
        return new JourneyDto(refreshToken, List.of(leg));
    }
}
