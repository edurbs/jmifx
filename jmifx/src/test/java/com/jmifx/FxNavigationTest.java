package com.jmifx;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class FxNavigationTest {

    @BeforeAll
    static void initToolkit() {
        FxTestKit.start();
    }

    @AfterEach
    void unbind() {
        FxNavigation.bind(null);
    }

    @Test
    void boundNavigatorReceivesNavigation() {
        StackPane pane = new StackPane();
        FxViewRegistry registry = new FxViewRegistry();
        registry.register("v", () -> stubView(new Label("via facade")));
        FxNavigation.bind(new FxNavigator(pane, registry));

        FxNavigation.navigateTo("v");

        assertEquals(1, pane.getChildren().size());
        assertEquals("via facade", ((Label) pane.getChildren().get(0)).getText());
    }

    @Test
    void unboundNavigationThrowsIllegalState() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> FxNavigation.navigateTo("v"));
        assertTrue(ex.getMessage().contains("not bound"), "message was: " + ex.getMessage());
    }

    @Test
    void applicationStartBindsNavigator() throws Exception {
        FxApplication app = new FxApplication() {
            @Override protected String getStartupViewId() { return "v"; }
            @Override protected void registerViews(FxViewRegistry r) {
                r.register("v", () -> stubView(new Label("startup")));
                r.register("other", () -> stubView(new Label("other view")));
            }
        };

        Stage[] stage = new Stage[1];
        CountDownLatch started = new CountDownLatch(1);
        javafx.application.Platform.runLater(() -> {
            try {
                stage[0] = new Stage();
                app.start(stage[0]);
            } finally {
                started.countDown();
            }
        });
        assertTrue(started.await(10, java.util.concurrent.TimeUnit.SECONDS));

        CountDownLatch navigated = new CountDownLatch(1);
        javafx.application.Platform.runLater(() -> {
            FxNavigation.navigateTo("other");
            navigated.countDown();
        });
        assertTrue(navigated.await(10, java.util.concurrent.TimeUnit.SECONDS));

        StackPane content = (StackPane) stage[0].getScene().getRoot();
        assertEquals(1, content.getChildren().size());
        assertEquals("other view", ((Label) content.getChildren().get(0)).getText());
        javafx.application.Platform.runLater(stage[0]::hide);
    }

    private static FxView stubView(Label label) {
        return new FxView() {
            @Override public String getId() { return "stub"; }
            @Override public javafx.scene.Parent getRoot() { return label; }
        };
    }
}
