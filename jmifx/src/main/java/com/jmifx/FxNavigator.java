package com.jmifx;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

/**
 * Swaps views inside a content pane. Any failure while creating or installing a
 * view (unknown id, throwing factory) results in an on-screen error label
 * instead of a blank pane — the browser console alone is not enough.
 */
public final class FxNavigator {

    private final Pane contentPane;
    private final FxViewRegistry registry;

    public FxNavigator(Pane contentPane, FxViewRegistry registry) {
        this.contentPane = contentPane;
        this.registry = registry;
    }

    public void navigateTo(String viewId) {
        try {
            FxView view = registry.createView(viewId);
            contentPane.getChildren().setAll(view.getRoot());
        } catch (Throwable t) {
            t.printStackTrace(); // browser/JVM console
            contentPane.getChildren().setAll(
                    new Label("Failed to load view '" + viewId + "': " + t));
        }
    }
}
