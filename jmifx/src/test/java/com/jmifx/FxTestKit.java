package com.jmifx;

import javafx.application.Platform;

/** Starts the JavaFX toolkit once per JVM (headless via Monocle). */
final class FxTestKit {

    private static boolean started;

    static synchronized void start() {
        if (started) {
            return;
        }
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // toolkit already initialized by an earlier test run in this JVM
        }
        started = true;
    }

    private FxTestKit() {
    }
}
