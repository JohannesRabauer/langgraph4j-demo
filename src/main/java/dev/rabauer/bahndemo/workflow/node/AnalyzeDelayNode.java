package dev.rabauer.bahndemo.workflow.node;

import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Re-searches alternative connections for the delayed journey's route. */
@Component
public class AnalyzeDelayNode implements NodeAction<DelayWorkflowState> {

    private final DbApiClient dbApiClient;

    public AnalyzeDelayNode(DbApiClient dbApiClient) {
        this.dbApiClient = dbApiClient;
    }

    @Override
    public Map<String, Object> apply(DelayWorkflowState state) throws Exception {
        JourneyDto original = state.originalJourney().orElse(null);
        int delaySeconds = state.delaySeconds().orElse(0);

        if (original == null || original.firstLeg() == null || original.lastLeg() == null) {
            return Map.of("alternatives", List.of(),
                    "log", List.of("analyzeDelay: no original journey to analyze"));
        }

        String fromId = original.firstLeg().origin().id();
        String toId = original.lastLeg().destination().id();

        String originalTripIdentity = DbApiClient.tripIdentity(original.refreshToken());
        List<JourneyDto> alternatives = dbApiClient.searchJourneys(fromId, toId, Instant.now()).stream()
                .filter(journey -> !DbApiClient.tripIdentity(journey.refreshToken()).equals(originalTripIdentity))
                .limit(3)
                .toList();

        String logLine = "analyzeDelay: delay of %ds detected, found %d alternative connection(s)"
                .formatted(delaySeconds, alternatives.size());

        return Map.of("alternatives", alternatives, "log", List.of(logLine));
    }
}
