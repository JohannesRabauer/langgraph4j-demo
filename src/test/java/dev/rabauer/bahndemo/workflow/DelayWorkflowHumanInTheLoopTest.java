package dev.rabauer.bahndemo.workflow;

import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.client.dto.LineDto;
import dev.rabauer.bahndemo.client.dto.LocationDto;
import dev.rabauer.bahndemo.service.MonitoredJourney;
import dev.rabauer.bahndemo.service.MonitoredJourneyRegistry;
import dev.rabauer.bahndemo.service.WorkflowOrchestrationService;
import org.awaitility.Awaitility;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Proves the actual human-in-the-loop mechanics: the graph pauses before "humanDecision" once
 * analyzeDelay/advisor have run (interruptBefore), and resuming with a decision (updateState +
 * GraphInput.resume()) drives it to completion. Uses the deterministic rule-based advisor
 * (bahn.advisor.enabled=false) and a mocked DbApiClient, so this test needs neither a live DB API
 * nor Ollama.
 */
@SpringBootTest
@TestPropertySource(properties = "bahn.advisor.enabled=false")
class DelayWorkflowHumanInTheLoopTest {

    @Autowired
    private WorkflowOrchestrationService orchestrationService;

    @Autowired
    private MonitoredJourneyRegistry registry;

    @Autowired
    private CompiledGraph<DelayWorkflowState> graph;

    @MockitoBean
    private DbApiClient dbApiClient;

    @Test
    void pausesForHumanDecisionThenResumesToOutcome() {
        LocationDto origin = new LocationDto("station", "8011160", "Berlin Hbf", null);
        LocationDto destination = new LocationDto("station", "8000261", "München Hbf", null);
        Instant now = Instant.now();

        JourneyDto originalJourney = journeyOf("original-token", origin, destination, now, now.plusSeconds(3600), "ICE 100");
        JourneyDto alternative = journeyOf("alt-1", origin, destination, now.plusSeconds(600), now.plusSeconds(4000), "IC 200");

        when(dbApiClient.searchJourneys(eq("8011160"), eq("8000261"), any(Instant.class)))
                .thenReturn(List.of(alternative));

        String journeyId = "test-journey-" + UUID.randomUUID();
        MonitoredJourney journey = new MonitoredJourney(journeyId, originalJourney);
        journey.setLastDelaySeconds(400);
        registry.register(journey);

        orchestrationService.startWorkflow(journey);

        RunnableConfig config = RunnableConfig.builder().threadId(journeyId).build();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            DelayWorkflowState state = graph.getState(config).state();
            assertThat(state.outcome()).isEmpty();
            assertThat(state.advisorRecommendation()).isPresent();
            assertThat(state.alternatives()).hasSize(1);
        });

        orchestrationService.resumeWithDecision(journeyId, HumanDecision.PICK_ALTERNATIVE, 0);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            DelayWorkflowState state = graph.getState(config).state();
            assertThat(state.outcome()).isPresent();
            assertThat(state.outcome().get()).contains("IC 200");
        });
    }

    private JourneyDto journeyOf(String refreshToken, LocationDto origin, LocationDto destination,
                                  Instant departure, Instant arrival, String lineName) {
        LegDto leg = new LegDto(origin, destination, departure, departure, null, arrival, arrival, null,
                new LineDto(lineName, "national"), false, false);
        return new JourneyDto(refreshToken, List.of(leg));
    }
}
