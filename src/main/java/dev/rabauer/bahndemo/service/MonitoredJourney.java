package dev.rabauer.bahndemo.service;

import dev.rabauer.bahndemo.client.dto.JourneyDto;
import dev.rabauer.bahndemo.workflow.DelayWorkflowState;

import java.util.function.Consumer;

/**
 * A journey the user has chosen to monitor. Its id doubles as the langgraph4j threadId, the single
 * correlation key that lets a browser click resume the exact right paused graph run.
 */
public class MonitoredJourney {

    private final String id;
    private volatile JourneyDto journey;
    private volatile int lastDelaySeconds;
    private volatile boolean workflowActive;

    /**
     * Set by the UI (on a genuine Vaadin UI thread) after capturing UI.getCurrent(); invoked from
     * background threads, so implementations must wrap their body in ui.access(...) themselves.
     */
    private volatile Consumer<DelayWorkflowState> uiCallback;

    public MonitoredJourney(String id, JourneyDto journey) {
        this.id = id;
        this.journey = journey;
    }

    public String getId() {
        return id;
    }

    public JourneyDto getJourney() {
        return journey;
    }

    public void setJourney(JourneyDto journey) {
        this.journey = journey;
    }

    public int getLastDelaySeconds() {
        return lastDelaySeconds;
    }

    public void setLastDelaySeconds(int lastDelaySeconds) {
        this.lastDelaySeconds = lastDelaySeconds;
    }

    public boolean isWorkflowActive() {
        return workflowActive;
    }

    public void setWorkflowActive(boolean workflowActive) {
        this.workflowActive = workflowActive;
    }

    public Consumer<DelayWorkflowState> getUiCallback() {
        return uiCallback;
    }

    public void setUiCallback(Consumer<DelayWorkflowState> uiCallback) {
        this.uiCallback = uiCallback;
    }
}
