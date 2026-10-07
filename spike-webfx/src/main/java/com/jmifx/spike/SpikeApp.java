package com.jmifx.spike;

import dev.webfx.kit.launcher.WebFxKitLauncher;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SpikeApp extends Application {

    private final Label label = new Label("Hello!");
    private final TextField field = new TextField();

    @Override
    public void start(Stage stage) {
        Button button = new Button("Greet");
        button.setOnAction(e ->
                label.setText(field.getText().isBlank() ? "Hello!" : "Hello, " + field.getText() + "!"));

        VBox root = new VBox(10, label, field, button);
        stage.setScene(new Scene(root, 400, 200));
        stage.setTitle("jmifx spike");
        stage.show();
    }

    public static void main(String[] args) {
        // WebFX requirement: launch via WebFxKitLauncher, not Application.launch()
        WebFxKitLauncher.launchApplication(SpikeApp::new, args);
    }
}
