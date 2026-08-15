package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.config.BahnDemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Polls monitored journeys for realtime delay data and triggers the langgraph4j workflow once a
 * journey's delay crosses bahn.delay.threshold-seconds.
 *
 * simulateDelay() bypasses the real API entirely, forcing a trigger - this is the demo-reliability
 * escape hatch so the human-in-the-loop flow can be shown on stream without depending on (or
 * waiting for) an actual train delay.
 */
@Service
public class DelayMonitorService {

    private static final Logger log = LoggerFactory.getLogger(DelayMonitorService.class);

    private final DbApiClient dbApiClient;
    private final MonitoredJourneyRegistry registry;
    private final BahnDemoProperties properties;
    private final WorkflowOrchestrationService orchestrationService;

    public DelayMonitorService(DbApiClient dbApiClient,
                                MonitoredJourneyRegistry registry,
                                BahnDemoProperties properties,
                                WorkflowOrchestrationService orchestrationService) {
        this.dbApiClient = dbApiClient;
        this.registry = registry;
        this.properties = properties;
        this.orchestrationService = orchestrationService;
    }

    @Scheduled(fixedDelayString = "${bahn.delay.poll-interval-ms:30000}")
    public void pollAll() {
        for (MonitoredJourney journey : registry.active()) {
            try {
                pollOne(journey);
            } catch (Exception e) {
                log.warn("Polling journey {} failed: {}", journey.getId(), e.toString());
            }
        }
    }

    private void pollOne(MonitoredJourney journey) {
        JourneyDto original = journey.getJourney();
        if (original == null || original.refreshToken() == null) {
            return;
        }
        JourneyDto refreshed = dbApiClient.refreshJourney(original.refreshToken());
        if (refreshed == null) {
            return;
        }
        journey.setJourney(refreshed);
        journey.setLastDelaySeconds(currentDelaySeconds(refreshed));
        maybeTrigger(journey);
    }

    private int currentDelaySeconds(JourneyDto journey) {
        LegDto lastLeg = journey.lastLeg();
        if (lastLeg == null) {
            return 0;
        }
        return Optional.ofNullable(lastLeg.arrivalDelay()).orElse(0);
    }

    /** Demo-safety trigger: forces a journey above the delay threshold without calling the DB API. */
    public void simulateDelay(String journeyId) {
        registry.find(journeyId).ifPresent(journey -> {
            journey.setLastDelaySeconds(properties.delay().thresholdSeconds() + 60);
            maybeTrigger(journey);
        });
    }

    private void maybeTrigger(MonitoredJourney journey) {
        if (!journey.isWorkflowActive() && journey.getLastDelaySeconds() >= properties.delay().thresholdSeconds()) {
            journey.setWorkflowActive(true);
            orchestrationService.startWorkflow(journey);
        }
    }
}
