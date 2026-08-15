package dev.rabauer.bahndemo.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.service.DelayMonitorService;
import dev.rabauer.bahndemo.service.MonitoredJourney;
import dev.rabauer.bahndemo.service.MonitoredJourneyRegistry;
import dev.rabauer.bahndemo.service.WorkflowOrchestrationService;
import dev.rabauer.bahndemo.ui.component.JourneyResultsGrid;
import dev.rabauer.bahndemo.ui.component.JourneySearchPanel;
import dev.rabauer.bahndemo.ui.component.MonitoringPanel;

import java.time.Instant;
import java.util.UUID;

/**
 * Single-view app (deliberately - keeps one UI instance alive for server push updates). Lays out
 * search -> results -> monitoring top to bottom and wires the components together.
 */
@Route("")
public class MainView extends VerticalLayout {

    private final DbApiClient dbApiClient;
    private final DelayMonitorService delayMonitorService;
    private final MonitoredJourneyRegistry registry;
    private final WorkflowOrchestrationService orchestrationService;
    private final MonitoringPanel monitoringPanel;

    private MonitoredJourney currentJourney;

    public MainView(DbApiClient dbApiClient,
                     DelayMonitorService delayMonitorService,
                     MonitoredJourneyRegistry registry,
                     WorkflowOrchestrationService orchestrationService,
                     JourneySearchPanel searchPanel,
                     JourneyResultsGrid resultsGrid,
                     MonitoringPanel monitoringPanel) {
        this.dbApiClient = dbApiClient;
        this.delayMonitorService = delayMonitorService;
        this.registry = registry;
        this.orchestrationService = orchestrationService;
        this.monitoringPanel = monitoringPanel;

        searchPanel.onSearch((from, to) -> {
            if (from == null || to == null) {
                return;
            }
            resultsGrid.setItems(dbApiClient.searchJourneys(from.id(), to.id(), Instant.now()));
        });

        resultsGrid.onMonitorRequested(this::startMonitoring);

        monitoringPanel.onSimulateDelay(() -> {
            if (currentJourney != null) {
                delayMonitorService.simulateDelay(currentJourney.getId());
            }
        });
        monitoringPanel.onDecision((decision, alternativeIndex) -> {
            if (currentJourney != null) {
                orchestrationService.resumeWithDecision(currentJourney.getId(), decision, alternativeIndex);
            }
        });

        add(searchPanel, resultsGrid, monitoringPanel);
    }

    private void startMonitoring(JourneyDto journey) {
        currentJourney = new MonitoredJourney(UUID.randomUUID().toString(), journey);
        UI ui = UI.getCurrent();
        currentJourney.setUiCallback(state -> ui.access(() -> monitoringPanel.render(state)));
        registry.register(currentJourney);
        monitoringPanel.startMonitoring(journey);
    }
}
