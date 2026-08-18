package dev.rabauer.bahndemo.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.rabauer.bahndemo.config.BahnDemoProperties;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Thin client for the public, unauthenticated api.transitous.org API (a MOTIS instance, aggregating
 * GTFS/GTFS-RT feeds across Europe, including Deutsche Bahn's own DELFI feed). Deutsche Bahn's own
 * vendo/movas backend (which powers v6.db.transport.rest via db-vendo-client) started blocking
 * third-party clients via Akamai TLS fingerprinting in 2026 - see
 * https://github.com/public-transport/db-vendo-client/issues/46 - so this app talks to transitous.org
 * instead, which is unaffected since it ingests DELFI's published GTFS feed rather than scraping DB's
 * app backend.
 *
 * Every call falls back to a hardcoded offline dataset on failure, so the demo (search, monitoring,
 * delay simulation) keeps working even if the live API is unreachable.
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
    private final BahnDemoProperties properties;

    public DbApiClient(RestClient dbApiRestClient, BahnDemoProperties properties) {
        this.restClient = dbApiRestClient;
        this.properties = properties;
    }

    /** GET /api/v1/geocode?text=... - station/address autocomplete. */
    public List<LocationDto> searchLocations(String query) {
        try {
            MotisGeocodeResult[] results = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/geocode")
                            .queryParam("text", query)
                            .build())
                    .retrieve()
                    .body(MotisGeocodeResult[].class);
            return results == null ? List.of() : Arrays.stream(results)
                    .filter(r -> r.lat() != null && r.lon() != null)
                    .map(DbApiClient::toLocationDto)
                    .limit(properties.api().defaultResults())
                    .toList();
        } catch (RestClientException e) {
            log.warn("api.transitous.org /api/v1/geocode unavailable ({}), using offline fallback data", e.toString());
            return fallbackLocations(query);
        }
    }

    /** GET /api/v1/plan?fromPlace=&toPlace=&time= - connection search between two stations/stops. */
    public List<JourneyDto> searchJourneys(String fromId, String toId, Instant when) {
        // MOTIS's "time" parser silently mis-parses Instant.toString()'s nanosecond-precision
        // fractional seconds (falls back to ~midnight instead of erroring) - truncate to millis,
        // which it parses correctly.
        Instant departure = (when != null ? when : Instant.now()).truncatedTo(ChronoUnit.MILLIS);
        try {
            MotisPlanResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/plan")
                            .queryParam("fromPlace", fromId)
                            .queryParam("toPlace", toId)
                            .queryParam("time", departure)
                            .queryParam("numItineraries", properties.api().defaultResults())
                            .build())
                    .retrieve()
                    .body(MotisPlanResponse.class);
            return response == null || response.itineraries() == null ? List.of()
                    : response.itineraries().stream()
                            .map(itinerary -> toJourneyDto(itinerary, fromId, toId, departure))
                            .toList();
        } catch (RestClientException e) {
            log.warn("api.transitous.org /api/v1/plan unavailable ({}), using offline fallback data", e.toString());
            return fallbackJourneys(fromId, toId, when);
        }
    }

    /**
     * Re-fetches realtime data (delays) for a previously returned journey. transitous.org has no
     * single-trip refresh endpoint, so this re-runs the same search that produced the journey and
     * picks out the itinerary for the same trip - which naturally reflects the latest realtime data.
     */
    public JourneyDto refreshJourney(String refreshToken) {
        if (refreshToken == null) {
            return null;
        }
        if (refreshToken.startsWith("offline-")) {
            return fallbackRefreshedJourney(refreshToken);
        }
        String[] parts = refreshToken.split("\\|", 5);
        if (parts.length != 5 || !"motis".equals(parts[0])) {
            log.warn("Unrecognized refresh token format: {}", refreshToken);
            return null;
        }
        String fromId = parts[1];
        String toId = parts[2];
        Instant when = Instant.parse(parts[3]);
        return searchJourneys(fromId, toId, when).stream()
                .filter(journey -> journey.refreshToken().equals(refreshToken))
                .findFirst()
                .orElse(null);
    }

    /**
     * Identifies "the same physical trip" across two searches, unlike raw refreshToken equality:
     * a motis refreshToken embeds the search's departure time, so the same trip found again by a
     * later search (e.g. when looking for alternatives to a delayed journey) gets a different
     * refreshToken even though it's the same train. Callers that need to recognize "this is the
     * journey I already have" (not just "this refreshToken string matches") should compare this
     * instead of the refreshToken itself.
     */
    public static String tripIdentity(String refreshToken) {
        if (refreshToken == null) {
            return null;
        }
        if (!refreshToken.startsWith("motis|")) {
            return refreshToken;
        }
        String[] parts = refreshToken.split("\\|", 5);
        return parts.length == 5 ? parts[4] : refreshToken;
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

    /**
     * geocode returns both STOP results (proper feed-qualified stop IDs, valid as fromPlace/toPlace)
     * and PLACE results (generic city/address matches with an OSM-node-style id that /api/v1/plan
     * rejects with "unknown feed id"). For anything but a STOP, use "lat,lon" instead - which
     * /api/v1/plan accepts directly and MOTIS resolves to the nearest stop itself.
     */
    private static LocationDto toLocationDto(MotisGeocodeResult result) {
        String id = "STOP".equals(result.type()) ? result.id() : (result.lat() + "," + result.lon());
        return new LocationDto(result.type(), id, result.name(),
                new LocationDto.Coordinates(result.lat(), result.lon()));
    }

    private static JourneyDto toJourneyDto(MotisItinerary itinerary, String fromId, String toId, Instant when) {
        List<LegDto> legs = itinerary.legs() == null ? List.of()
                : itinerary.legs().stream().map(DbApiClient::toLegDto).toList();
        String tripId = itinerary.legs() == null ? null : itinerary.legs().stream()
                .map(MotisLeg::tripId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(itinerary.id());
        String refreshToken = String.join("|", "motis", fromId, toId, when.toString(), String.valueOf(tripId));
        return new JourneyDto(refreshToken, legs);
    }

    private static LegDto toLegDto(MotisLeg leg) {
        boolean walking = "WALK".equals(leg.mode());
        LocationDto origin = toLocationDto(leg.from());
        LocationDto destination = toLocationDto(leg.to());
        LineDto line = walking ? null : new LineDto(leg.displayName(), toProduct(leg.mode()));
        return new LegDto(
                origin, destination,
                leg.startTime(), leg.scheduledStartTime(), delaySeconds(leg.scheduledStartTime(), leg.startTime()),
                leg.endTime(), leg.scheduledEndTime(), delaySeconds(leg.scheduledEndTime(), leg.endTime()),
                line, Boolean.TRUE.equals(leg.cancelled()), walking);
    }

    private static LocationDto toLocationDto(MotisPlace place) {
        if (place == null) {
            return null;
        }
        String id = place.stopId() != null ? place.stopId() : place.name();
        LocationDto.Coordinates coordinates = place.lat() != null && place.lon() != null
                ? new LocationDto.Coordinates(place.lat(), place.lon()) : null;
        return new LocationDto(place.stopId() != null ? "stop" : "address", id, place.name(), coordinates);
    }

    private static Integer delaySeconds(Instant planned, Instant actual) {
        if (planned == null || actual == null) {
            return null;
        }
        long seconds = Duration.between(planned, actual).getSeconds();
        return seconds > 0 ? (int) seconds : null;
    }

    private static String toProduct(String mode) {
        if (mode == null) {
            return "unknown";
        }
        return switch (mode) {
            case "HIGHSPEED_RAIL" -> "nationalExpress";
            case "LONG_DISTANCE", "RAIL" -> "national";
            case "REGIONAL_RAIL", "REGIONAL_FAST_RAIL" -> "regional";
            case "SUBURBAN" -> "suburban";
            case "SUBWAY" -> "subway";
            case "TRAM" -> "tram";
            case "BUS", "COACH" -> "bus";
            case "FERRY" -> "ferry";
            default -> mode.toLowerCase(Locale.ROOT);
        };
    }

    /** Flat geocode result: {"type":"STOP","id":"...","name":"...","lat":...,"lon":...}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MotisGeocodeResult(String type, String id, String name, Double lat, Double lon) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MotisPlanResponse(List<MotisItinerary> itineraries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MotisItinerary(String id, List<MotisLeg> legs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MotisLeg(
            String mode,
            MotisPlace from,
            MotisPlace to,
            Instant startTime,
            Instant endTime,
            Instant scheduledStartTime,
            Instant scheduledEndTime,
            String displayName,
            String tripId,
            Boolean cancelled) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MotisPlace(String name, String stopId, Double lat, Double lon) {
    }
}
