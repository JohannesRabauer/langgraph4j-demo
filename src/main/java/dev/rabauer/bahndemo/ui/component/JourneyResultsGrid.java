package dev.rabauer.bahndemo.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Shows journey search results with a "Monitor this connection" action per row.
 *
 * TODO(stream): add columns for line/product and planned vs. actual departure/arrival/delay (from
 * JourneyDto#firstLeg()/lastLeg()) - currently only a placeholder column.
 */
@Component
public class JourneyResultsGrid extends Grid<JourneyDto> {

    private Consumer<JourneyDto> monitorListener = journey -> { };

    public JourneyResultsGrid() {
        addColumn(JourneyDto::refreshToken).setHeader("Journey");
        addComponentColumn(journey -> {
            Button monitorButton = new Button("Monitor this connection");
            monitorButton.addClickListener(e -> monitorListener.accept(journey));
            return monitorButton;
        }).setHeader("Action");
    }

    public void onMonitorRequested(Consumer<JourneyDto> listener) {
        this.monitorListener = listener;
    }
}
