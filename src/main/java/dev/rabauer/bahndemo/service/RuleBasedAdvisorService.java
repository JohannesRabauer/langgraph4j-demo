package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
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
    public String recommend(int delaySeconds, List<JourneyDto> alternatives) {
        if (alternatives.isEmpty()) {
            return "No alternative connections were found; recommend keeping the original connection and waiting out the %ds delay."
                    .formatted(delaySeconds);
        }

        JourneyDto best = alternatives.stream()
                .min(Comparator.comparing(this::effectiveArrival))
                .orElseThrow();

        LegDto lastLeg = best.lastLeg();
        LegDto firstLeg = best.firstLeg();
        String line = firstLeg != null && firstLeg.line() != null ? firstLeg.line().name() : "an alternative connection";
        return "Recommend switching to %s, arriving earliest at %s."
                .formatted(line, lastLeg != null ? effectiveArrival(best) : "an unknown time");
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
