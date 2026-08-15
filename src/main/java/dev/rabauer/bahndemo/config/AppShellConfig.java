package dev.rabauer.bahndemo.config;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.aura.Aura;

/**
 * Enables Vaadin server push - required because delay polling and langgraph4j graph execution run
 * on background threads, and their UI updates must be pushed reactively rather than waiting for the
 * next client request. This is the one place @Push belongs (Vaadin allows exactly one
 * AppShellConfigurator per app).
 *
 * Vaadin 25 only auto-loads the Aura theme when NO AppShellConfigurator is present in the app -
 * defining one (as above, for @Push) opts out of that default, so the theme has to be loaded
 * explicitly here or every component renders with zero styling (browser-default fonts/colors).
 */
@Push(PushMode.AUTOMATIC)
@StyleSheet(Aura.STYLESHEET)
public class AppShellConfig implements AppShellConfigurator {
}
