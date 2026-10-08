package com.jmifx.demo.client;

import com.jmifx.FxNavigation;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Controller for hello-view.fxml — standard JavaFX/Scene Builder idiom.
 * Fields are package-visible (jmifx convention: generated code injects them
 * directly, zero reflection).
 */
public class HelloViewController {

    Label greetingLabel;
    TextField nameField;
    Button greetButton;
    Button cityButton;

    public void initialize() {
    }

    public void greet(ActionEvent event) {
        String name = nameField.getText();
        greetingLabel.setText(name == null || name.isBlank()
                ? "Hello!"
                : "Hello, " + name + "!");
    }

    public void openCityDetail(ActionEvent event) {
        FxNavigation.navigateTo("city-detail-view");
    }
}
