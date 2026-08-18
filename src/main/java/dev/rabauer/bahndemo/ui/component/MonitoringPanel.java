package dev.rabauer.bahndemo.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.client.dto.LegDto;
import dev.rabauer.bahndemo.util.TimeFormat;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import dev.rabauer.bahndemo.workflow.HumanDecision;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Shows monitoring status for one journey: a "Simulate delay" trigger, and - once the workflow
 * pauses (state.outcome() empty) - a clear "your decision is needed" heading, the advisor's
 * recommendation as its own block, an "Accept suggested" button naming the specific connection it
 * refers to, one "switch to this" button per found alternative (the recommended one marked), and a
 * "Keep waiting" fallback. Also renders state.log() as a timeline.
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
    private final Span delayHeading = new Span();
    private final Paragraph rationaleBlock = new Paragraph();
    private final Button simulateDelayButton = new Button("Simulate a delay (demo)");
    private final Button acceptSuggestedButton = new Button("Accept suggested");
    private final Button keepWaitingButton = new Button("Keep waiting on the original connection");
    private final VerticalLayout alternativesLayout = new VerticalLayout();
    private final UnorderedList timeline = new UnorderedList();

    private Runnable simulateDelayListener = () -> { };
    private BiConsumer<HumanDecision, Integer> decisionListener = (decision, index) -> { };

    public MonitoringPanel() {
        simulateDelayButton.setTooltipText("For this demo: force a delay now instead of waiting for a real one.");
        simulateDelayButton.addClickListener(e -> simulateDelayListener.run());
        acceptSuggestedButton.addThemeVariants(ButtonVariant.PRIMARY);
        acceptSuggestedButton.addClickListener(e -> decisionListener.accept(HumanDecision.ACCEPT_SUGGESTED, null));
        keepWaitingButton.addThemeVariants(ButtonVariant.TERTIARY);
        keepWaitingButton.addClickListener(e -> decisionListener.accept(HumanDecision.KEEP_WAITING, null));

        delayHeading.getStyle().set("font-weight", "bold");
        alternativesLayout.setPadding(false);
        alternativesLayout.setSpacing(false);

        setPausedControlsVisible(false);
        add(statusLabel, simulateDelayButton, delayHeading, rationaleBlock, acceptSuggestedButton,
                alternativesLayout, keepWaitingButton, new Div(new Span("Timeline:")), timeline);
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
        statusLabel.setVisible(true);
        setPausedControlsVisible(false);
        alternativesLayout.removeAll();
        timeline.removeAll();
    }

    public void render(DelayWorkflowState state) {
        renderTimeline(state.log());

        if (state.outcome().isPresent()) {
            statusLabel.setText("Done: " + state.outcome().get());
            statusLabel.setVisible(true);
            setPausedControlsVisible(false);
            alternativesLayout.removeAll();
        } else {
            statusLabel.setVisible(false);
            int delayMinutes = state.delaySeconds().orElse(0) / 60;
            delayHeading.setText("Delay detected (%d min) - your decision is needed:".formatted(delayMinutes));
            rationaleBlock.setText(state.advisorRecommendation().orElse("Waiting for the assistant's recommendation..."));

            List<JourneyDto> alternatives = state.alternatives();
            Integer recommendedIndex = state.advisorRecommendedIndex().orElse(null);

            acceptSuggestedButton.setVisible(recommendedIndex != null && recommendedIndex >= 0
                    && recommendedIndex < alternatives.size());
            if (acceptSuggestedButton.isVisible()) {
                acceptSuggestedButton.setText("Accept: " + describe(alternatives.get(recommendedIndex)));
            }
            renderAlternatives(alternatives, recommendedIndex);
            setPausedControlsVisible(true);
        }
    }

    private void renderAlternatives(List<JourneyDto> alternatives, Integer recommendedIndex) {
        alternativesLayout.removeAll();
        for (int i = 0; i < alternatives.size(); i++) {
            int index = i;
            JourneyDto journey = alternatives.get(i);
            String label = "Switch to: " + describe(journey) + (Integer.valueOf(i).equals(recommendedIndex) ? " (recommended)" : "");
            Button chooseButton = new Button(label);
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
        return "%s, arrives %s".formatted(line, TimeFormat.format(lastLeg.plannedArrival()));
    }

    private void renderTimeline(List<String> log) {
        timeline.removeAll();
        for (String entry : log) {
            timeline.add(new ListItem(entry));
        }
    }

    private void setPausedControlsVisible(boolean visible) {
        delayHeading.setVisible(visible);
        rationaleBlock.setVisible(visible);
        if (!visible) {
            acceptSuggestedButton.setVisible(false);
        }
        alternativesLayout.setVisible(visible);
        keepWaitingButton.setVisible(visible);
    }
}
