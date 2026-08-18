package dev.rabauer.bahndemo.workflow;

/** The choices a human can make once the delay workflow pauses for input. */
public enum HumanDecision {
    ACCEPT_SUGGESTED,
    PICK_ALTERNATIVE,
    KEEP_WAITING
}
