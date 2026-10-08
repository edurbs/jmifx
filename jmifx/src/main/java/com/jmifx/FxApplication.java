package com.jmifx;

import dev.webfx.kit.launcher.WebFxKitLauncher;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.function.Supplier;

/**
 * Base class for jmifx applications. Subclasses declare their views and the
 * startup view id; {@link #launchApp(Supplier, String...)} is the entry point
 * used from {@code main} — it goes through WebFX's launcher, which works both
 * on the JVM and in the browser (compiled by TeaVM). Note: standard
 * {@code Application.launch()} does NOT run in the browser.
 */
public abstract class FxApplication extends Application {

    private final FxViewRegistry registry = new FxViewRegistry();

    /** The view id shown first — by convention the FXML base name. */
    protected abstract String getStartupViewId();

    /** Register view factories (typically via the generated FxViewsIndex). */
    protected abstract void registerViews(FxViewRegistry registry);

    /** Overridable scene factory; 800x600 by default. */
    protected Scene createScene(Parent root) {
        return new Scene(root, 800, 600);
    }

    @Override
    public void start(Stage stage) {
        registerViews(registry);
        StackPane contentPane = new StackPane();
        FxNavigator navigator = new FxNavigator(contentPane, registry);
        FxNavigation.bind(navigator);
        navigator.navigateTo(getStartupViewId());
        stage.setScene(createScene(contentPane));
        stage.show();
    }

    /**
     * Launches the application on any platform (JVM or browser/Wasm).
     * Usage: {@code public static void main(String[] a) { launchApp(DemoFxApp::new, a); }}
     */
    public static void launchApp(Supplier<? extends FxApplication> appFactory, String... args) {
        WebFxKitLauncher.launchApplication(appFactory::get, args);
    }
}
