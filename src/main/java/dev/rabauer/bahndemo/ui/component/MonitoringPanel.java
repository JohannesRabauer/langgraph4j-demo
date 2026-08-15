package dev.rabauer.bahndemo.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;
import dev.rabauer.bahndemo.workflow.HumanDecision;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

/**
 * Shows monitoring status for one journey: a "Simulate delay" trigger, and - once the workflow
 * pauses (state.outcome() empty) - the advisor's recommendation and decision buttons.
 *
 * render(...) must be called from within UI.access(...) by the caller (see MainView), since it can
 * be invoked from background threads (scheduler / workflow executor).
 *
 * TODO(stream): render alternatives as a list/grid with per-row "pick this one" buttons (currently
 * pickAlternativeButton always resumes with a null index), and show state.log() as a timeline.
 */
@Component
public class MonitoringPanel extends VerticalLayout {

    private final Span statusLabel = new Span("Not monitoring");
    private final Button simulateDelayButton = new Button("Simulate delay");
    private final Button acceptSuggestedButton = new Button("Accept suggested");
    private final Button pickAlternativeButton = new Button("Pick alternative");
    private final Button keepWaitingButton = new Button("Keep waiting");

    private Runnable simulateDelayListener = () -> { };
    private BiConsumer<HumanDecision, Integer> decisionListener = (decision, index) -> { };

    public MonitoringPanel() {
        simulateDelayButton.addClickListener(e -> simulateDelayListener.run());
        acceptSuggestedButton.addClickListener(e -> decisionListener.accept(HumanDecision.ACCEPT_SUGGESTED, null));
        pickAlternativeButton.addClickListener(e -> decisionListener.accept(HumanDecision.PICK_ALTERNATIVE, null));
        keepWaitingButton.addClickListener(e -> decisionListener.accept(HumanDecision.KEEP_WAITING, null));

        setDecisionButtonsVisible(false);
        add(statusLabel, simulateDelayButton, acceptSuggestedButton, pickAlternativeButton, keepWaitingButton);
    }

    public void onSimulateDelay(Runnable listener) {
        this.simulateDelayListener = listener;
    }

    public void onDecision(BiConsumer<HumanDecision, Integer> listener) {
        this.decisionListener = listener;
    }

    public void render(DelayWorkflowState state) {
        if (state.outcome().isPresent()) {
            statusLabel.setText("Done: " + state.outcome().get());
            setDecisionButtonsVisible(false);
        } else {
            statusLabel.setText("Delay detected (" + state.delaySeconds().orElse(0) + "s) - "
                    + state.advisorRecommendation().orElse("awaiting advisor"));
            setDecisionButtonsVisible(true);
        }
    }

    private void setDecisionButtonsVisible(boolean visible) {
        acceptSuggestedButton.setVisible(visible);
        pickAlternativeButton.setVisible(visible);
        keepWaitingButton.setVisible(visible);
    }
}
