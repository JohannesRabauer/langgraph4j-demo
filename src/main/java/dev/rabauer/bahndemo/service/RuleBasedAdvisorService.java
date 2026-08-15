package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default, always-active advisor - no @ConditionalOnProperty guard, so this bean exists whenever
 * bahn.advisor.enabled is false (or unset), and the workflow always has an advisor to call.
 *
 * TODO(stream): pick the alternative with the earliest actual (planned + delay) arrival and return
 * a one-sentence recommendation referencing it.
 */
@Service
public class RuleBasedAdvisorService implements AdvisorService {

    @Override
    public String recommend(int delaySeconds, List<JourneyDto> alternatives) {
        return "TODO rule-based recommendation";
    }
}
