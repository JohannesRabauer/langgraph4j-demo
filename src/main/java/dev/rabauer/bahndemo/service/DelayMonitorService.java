package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.config.BahnDemoProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
        // TODO(stream): for each registry.active() journey, call
        // dbApiClient.refreshJourney(journey.getJourney().refreshToken()), derive the current delay
        // in seconds from the refreshed legs, journey.setLastDelaySeconds(...), then maybeTrigger(journey).
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
