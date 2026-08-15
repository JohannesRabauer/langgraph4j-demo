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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin client for the public, unauthenticated v6.db.transport.rest API (backed by db-vendo-client).
 * Rate limit: 100 requests/minute - keep polling intervals well under that.
 *
 * Every call falls back to a hardcoded offline dataset on failure, so the demo (search, monitoring,
 * delay simulation) keeps working even if the live API is unreachable or rate-limited - a real
 * condition observed for hours at a time while building this client, not a hypothetical one. The
 * dataset spans several countries (not just Germany) so cross-border searches still return results
 * while the live API is down.
 */
@Component
public class DbApiClient {

    private static final Logger log = LoggerFactory.getLogger(DbApiClient.class);

    private static final List<LocationDto> OFFLINE_LOCATIONS = List.of(
            location("off:berlin-hbf", "Berlin Hbf", 52.525589, 13.369548),
            location("off:muenchen-hbf", "München Hbf", 48.140228, 11.558339),
            location("off:hamburg-hbf", "Hamburg Hbf", 53.552736, 10.006909),
            location("off:frankfurt-hbf", "Frankfurt(Main) Hbf", 50.106932, 8.663789),
            location("off:koeln-hbf", "Köln Hbf", 50.943029, 6.958729),
            location("off:stuttgart-hbf", "Stuttgart Hbf", 48.784083, 9.181635),
            location("off:duesseldorf-hbf", "Düsseldorf Hbf", 51.219960, 6.794260),
            location("off:leipzig-hbf", "Leipzig Hbf", 51.345377, 12.383275),
            location("off:dresden-hbf", "Dresden Hbf", 51.040562, 13.732450),
            location("off:nuernberg-hbf", "Nürnberg Hbf", 49.445614, 11.082989),
            location("off:hannover-hbf", "Hannover Hbf", 52.377531, 9.741794),
            location("off:bremen-hbf", "Bremen Hbf", 53.083244, 8.813580),
            location("off:roma-termini", "Roma Termini", 41.900930, 12.501650),
            location("off:milano-centrale", "Milano Centrale", 45.486339, 9.204449),
            location("off:venezia-santa-lucia", "Venezia Santa Lucia", 45.441269, 12.320940),
            location("off:firenze-smn", "Firenze Santa Maria Novella", 43.776759, 11.247861),
            location("off:napoli-centrale", "Napoli Centrale", 40.852669, 14.271289),
            location("off:wien-hbf", "Wien Hauptbahnhof", 48.185090, 16.377220),
            location("off:salzburg-hbf", "Salzburg Hbf", 47.813130, 13.045060),
            location("off:zuerich-hb", "Zürich HB", 47.378177, 8.540212),
            location("off:basel-sbb", "Basel SBB", 47.547409, 7.589568),
            location("off:paris-est", "Paris Gare de l'Est", 48.876660, 2.359070),
            location("off:amsterdam-centraal", "Amsterdam Centraal", 52.378890, 4.900280)
    );

    private static final Map<String, LocationDto> OFFLINE_LOCATIONS_BY_ID = OFFLINE_LOCATIONS.stream()
            .collect(Collectors.toMap(LocationDto::id, loc -> loc));

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
        return OFFLINE_LOCATIONS.stream()
                .filter(loc -> needle.isBlank() || loc.name().toLowerCase().contains(needle))
                .toList();
    }

    private List<JourneyDto> fallbackJourneys(String fromId, String toId, Instant when) {
        LocationDto origin = OFFLINE_LOCATIONS_BY_ID.getOrDefault(fromId,
                new LocationDto("station", fromId, fromId, null));
        LocationDto destination = OFFLINE_LOCATIONS_BY_ID.getOrDefault(toId,
                new LocationDto("station", toId, toId, null));
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

    private static LocationDto location(String id, String name, double latitude, double longitude) {
        return new LocationDto("station", id, name, new LocationDto.Coordinates(latitude, longitude));
    }
}
