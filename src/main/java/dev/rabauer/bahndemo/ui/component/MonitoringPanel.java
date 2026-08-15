package dev.rabauer.bahndemo.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import dev.rabauer.bahndemo.workflow.HumanDecision;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Shows monitoring status for one journey: a "Simulate delay" trigger, and - once the workflow
 * pauses (state.outcome() empty) - the advisor's recommendation, one "choose this" button per
 * found alternative, and a "Keep waiting" fallback. Also renders state.log() as a timeline.
 *
 * render(...) must be called from within UI.access(...) by the caller (see MainView), since it can
 * be invoked from background threads (scheduler / workflow executor).
 *
 * Prototype-scoped: see JourneySearchPanel for why (a Vaadin component can only belong to one UI).
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MonitoringPanel extends VerticalLayout {

    private final Span statusLabel = new Span("Not monitoring");
    private final Button simulateDelayButton = new Button("Simulate delay");
    private final Button acceptSuggestedButton = new Button("Accept suggested");
    private final Button keepWaitingButton = new Button("Keep waiting");
    private final VerticalLayout alternativesLayout = new VerticalLayout();
    private final UnorderedList timeline = new UnorderedList();

    private Runnable simulateDelayListener = () -> { };
    private BiConsumer<HumanDecision, Integer> decisionListener = (decision, index) -> { };

    public MonitoringPanel() {
        simulateDelayButton.addClickListener(e -> simulateDelayListener.run());
        acceptSuggestedButton.addClickListener(e -> decisionListener.accept(HumanDecision.ACCEPT_SUGGESTED, null));
        keepWaitingButton.addClickListener(e -> decisionListener.accept(HumanDecision.KEEP_WAITING, null));

        alternativesLayout.setPadding(false);
        alternativesLayout.setSpacing(false);

        setDecisionControlsVisible(false);
        add(statusLabel, simulateDelayButton, acceptSuggestedButton, alternativesLayout, keepWaitingButton,
                new Div(new Span("Timeline:")), timeline);
    }

    public void onSimulateDelay(Runnable listener) {
        this.simulateDelayListener = listener;
    }

    public void onDecision(BiConsumer<HumanDecision, Integer> listener) {
        this.decisionListener = listener;
    }

    /** Immediate feedback for clicking "Monitor this connection" - before any delay has been detected. */
    public void startMonitoring(JourneyDto journey) {
        statusLabel.setText("Monitoring: " + describe(journey));
        setDecisionControlsVisible(false);
        alternativesLayout.removeAll();
        timeline.removeAll();
    }

    public void render(DelayWorkflowState state) {
        renderTimeline(state.log());

        if (state.outcome().isPresent()) {
            statusLabel.setText("Done: " + state.outcome().get());
            setDecisionControlsVisible(false);
            alternativesLayout.removeAll();
        } else {
            statusLabel.setText("Delay detected (" + state.delaySeconds().orElse(0) + "s) - "
                    + state.advisorRecommendation().orElse("awaiting advisor"));
            setDecisionControlsVisible(true);
            renderAlternatives(state.alternatives());
        }
    }

    private void renderAlternatives(List<JourneyDto> alternatives) {
        alternativesLayout.removeAll();
        for (int i = 0; i < alternatives.size(); i++) {
            int index = i;
            JourneyDto journey = alternatives.get(i);
            Button chooseButton = new Button("Choose: " + describe(journey));
            chooseButton.addClickListener(e -> decisionListener.accept(HumanDecision.PICK_ALTERNATIVE, index));
            alternativesLayout.add(new HorizontalLayout(chooseButton));
        }
    }

    private String describe(JourneyDto journey) {
        LegDto firstLeg = journey.firstLeg();
        LegDto lastLeg = journey.lastLeg();
        if (firstLeg == null || lastLeg == null) {
            return journey.refreshToken();
        }
        String line = firstLeg.line() != null ? firstLeg.line().name() : "unknown line";
        return "%s, arrives %s".formatted(line, lastLeg.plannedArrival());
    }

    private void renderTimeline(List<String> log) {
        timeline.removeAll();
        for (String entry : log) {
            timeline.add(new ListItem(entry));
        }
    }

    private void setDecisionControlsVisible(boolean visible) {
        acceptSuggestedButton.setVisible(visible);
        alternativesLayout.setVisible(visible);
        keepWaitingButton.setVisible(visible);
    }
}
