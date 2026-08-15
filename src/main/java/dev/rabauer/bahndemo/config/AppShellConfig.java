package dev.rabauer.bahndemo.config;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;

/**
 * Enables Vaadin server push - required because delay polling and langgraph4j graph execution run
 * on background threads, and their UI updates must be pushed reactively rather than waiting for the
 * next client request. This is the one place @Push belongs (Vaadin allows exactly one
 * AppShellConfigurator per app).
 */
@Push(PushMode.AUTOMATIC)
public class AppShellConfig implements AppShellConfigurator {
}
