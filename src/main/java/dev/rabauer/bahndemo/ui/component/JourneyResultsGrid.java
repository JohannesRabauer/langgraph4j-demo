package dev.rabauer.bahndemo.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Shows journey search results with a "Monitor this connection" action per row.
 *
 * Prototype-scoped: see JourneySearchPanel for why (a Vaadin component can only belong to one UI).
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class JourneyResultsGrid extends Grid<JourneyDto> {

    private Consumer<JourneyDto> monitorListener = journey -> { };

    public JourneyResultsGrid() {
        addColumn(journey -> lineName(journey.firstLeg())).setHeader("Line");
        addColumn(journey -> format(journey.firstLeg() != null ? journey.firstLeg().plannedDeparture() : null))
                .setHeader("Departure");
        addColumn(journey -> format(journey.lastLeg() != null ? journey.lastLeg().plannedArrival() : null))
                .setHeader("Arrival");
        addColumn(journey -> delayText(journey.lastLeg())).setHeader("Delay");
        addComponentColumn(journey -> {
            Button monitorButton = new Button("Monitor this connection");
            monitorButton.addClickListener(e -> monitorListener.accept(journey));
            return monitorButton;
        }).setHeader("Action");
    }

    public void onMonitorRequested(Consumer<JourneyDto> listener) {
        this.monitorListener = listener;
    }

    private String lineName(LegDto leg) {
        return leg != null && leg.line() != null ? leg.line().name() : "-";
    }

    private String format(java.time.Instant instant) {
        return instant != null ? instant.toString() : "-";
    }

    private String delayText(LegDto leg) {
        if (leg == null || leg.arrivalDelay() == null) {
            return "on time";
        }
        return "+" + (leg.arrivalDelay() / 60) + " min";
    }
}
