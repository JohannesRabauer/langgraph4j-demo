package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static dev.rabauer.bahndemo.workflow.DelayWorkflowState.*;

/**
 * First graph node: searches for alternative journeys that depart after now, excluding the
 * original trip. Produces {@code alternatives} and a {@code log} entry.
 */
public class AnalyzeDelayNode {

    private final DbApiClient dbApiClient;

    public AnalyzeDelayNode(DbApiClient dbApiClient) {
        this.dbApiClient = dbApiClient;
    }

    public Map<String, Object> apply(DelayWorkflowState state) {
        List<JourneyDto> alternatives = findAlternatives(state);
        return Map.of(
                ALTERNATIVES, alternatives,
                LOG, "Found %d alternative connection(s)".formatted(alternatives.size())
        );
    }

    private List<JourneyDto> findAlternatives(DelayWorkflowState state) {
        JourneyDto original = state.originalJourney().orElse(null);
        if (original == null) {
            return List.of();
        }
        LegDto firstLeg = original.firstLeg();
        LegDto lastLeg  = original.lastLeg();
        if (firstLeg == null || lastLeg == null
                || firstLeg.origin() == null || lastLeg.destination() == null) {
            return List.of();
        }
        String originalTripIdentity = DbApiClient.tripIdentity(original.refreshToken());
        return dbApiClient
                .searchJourneys(firstLeg.origin().id(), lastLeg.destination().id(), Instant.now())
                .stream()
                .filter(c -> !DbApiClient.tripIdentity(c.refreshToken()).equals(originalTripIdentity))
                .toList();
    }
}
