package com.jmifx;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FxNavigatorTest {

    @org.junit.jupiter.api.BeforeAll
    static void initToolkit() {
        FxTestKit.start();
    }

    @Test
    void navigateToInstallsViewRootAsOnlyChild() {
        StackPane pane = new StackPane();
        FxViewRegistry registry = new FxViewRegistry();
        registry.register("v", () -> stubView("v", new Label("content")));
        FxNavigator navigator = new FxNavigator(pane, registry);

        navigator.navigateTo("v");

        assertEquals(1, pane.getChildren().size());
        assertInstanceOf(Label.class, pane.getChildren().get(0));
        assertEquals("content", ((Label) pane.getChildren().get(0)).getText());
    }

    @Test
    void navigateToReplacesPreviousView() {
        StackPane pane = new StackPane();
        FxViewRegistry registry = new FxViewRegistry();
        registry.register("a", () -> stubView("a", new Label("A")));
        registry.register("b", () -> stubView("b", new Label("B")));
        FxNavigator navigator = new FxNavigator(pane, registry);

        navigator.navigateTo("a");
        navigator.navigateTo("b");

        assertEquals(1, pane.getChildren().size());
        assertEquals("B", ((Label) pane.getChildren().get(0)).getText());
    }

    @Test
    void unknownViewIdShowsErrorLabel() {
        StackPane pane = new StackPane();
        FxNavigator navigator = new FxNavigator(pane, new FxViewRegistry());

        navigator.navigateTo("missing");

        assertEquals(1, pane.getChildren().size());
        Label error = (Label) pane.getChildren().get(0);
        assertTrue(error.getText().contains("Failed to load view 'missing'"),
                "error text was: " + error.getText());
    }

    @Test
    void failingFactoryShowsErrorLabelWithExceptionMessage() {
        StackPane pane = new StackPane();
        FxViewRegistry registry = new FxViewRegistry();
        registry.register("boom", () -> { throw new IllegalStateException("kaboom"); });
        FxNavigator navigator = new FxNavigator(pane, registry);

        navigator.navigateTo("boom");

        Label error = (Label) pane.getChildren().get(0);
        assertTrue(error.getText().contains("kaboom"), "error text was: " + error.getText());
    }

    private static FxView stubView(String id, javafx.scene.Parent root) {
        return new FxView() {
            @Override public String getId() { return id; }
            @Override public javafx.scene.Parent getRoot() { return root; }
        };
    }
}
