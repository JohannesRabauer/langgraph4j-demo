package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.util.TimeFormat;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Default, always-active advisor - no @ConditionalOnProperty guard, so this bean exists whenever
 * bahn.advisor.enabled is false (or unset), and the workflow always has an advisor to call.
 *
 * Picks the alternative with the earliest effective arrival (planned arrival + any known delay).
 */
@Service
public class RuleBasedAdvisorService implements AdvisorService {

    @Override
    public AdvisorRecommendation recommend(int delaySeconds, List<JourneyDto> alternatives) {
        if (alternatives.isEmpty()) {
            return new AdvisorRecommendation(null,
                    "No alternative connections were found; recommend keeping the original connection and waiting out the %ds delay."
                            .formatted(delaySeconds));
        }

        int bestIndex = 0;
        Instant bestArrival = effectiveArrival(alternatives.get(0));
        for (int i = 1; i < alternatives.size(); i++) {
            Instant candidate = effectiveArrival(alternatives.get(i));
            if (candidate.isBefore(bestArrival)) {
                bestArrival = candidate;
                bestIndex = i;
            }
        }

        LegDto firstLeg = alternatives.get(bestIndex).firstLeg();
        String line = firstLeg != null && firstLeg.line() != null ? firstLeg.line().name() : "an alternative connection";
        String rationale = "Recommend switching to %s, arriving earliest at %s."
                .formatted(line, TimeFormat.format(bestArrival));
        return new AdvisorRecommendation(bestIndex, rationale);
    }

    private Instant effectiveArrival(JourneyDto journey) {
        LegDto lastLeg = journey.lastLeg();
        if (lastLeg == null || lastLeg.plannedArrival() == null) {
            return Instant.MAX;
        }
        int delay = Optional.ofNullable(lastLeg.arrivalDelay()).orElse(0);
        return lastLeg.plannedArrival().plusSeconds(delay);
    }
}
