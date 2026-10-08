package com.jmifx.demo.client;

import com.jmifx.FxHttp;
import com.jmifx.FxJson;
import com.jmifx.FxNavigation;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Controller for city-detail-view.fxml — Jmix detail-view idiom with one field.
 * Save POSTs the city name to the generic REST API (Bearer via FxAuth/FxHttp).
 */
public class CityDetailViewController {

    Label titleLabel;
    TextField cityField;
    Button saveButton;
    Button backButton;
    Label statusLabel;

    public void initialize() {
    }

    public void save(ActionEvent event) {
        String name = cityField.getText();
        if (name == null || name.isBlank()) {
            statusLabel.setText("City name required");
            return;
        }
        String trimmed = name.trim();
        saveButton.setDisable(true);
        statusLabel.setText("Saving…");
        FxHttp.post("/rest/entities/City", FxJson.obj("name", trimmed), new FxHttp.Listener() {
            @Override
            public void onResult(int statusCode, String body) {
                if (statusCode >= 200 && statusCode < 300) {
                    statusLabel.setText("Saved: " + trimmed);
                } else {
                    statusLabel.setText("Save failed (HTTP " + statusCode + ")");
                }
                saveButton.setDisable(false);
            }

            @Override
            public void onFailure(Throwable t) {
                statusLabel.setText("Save failed: " + t.getMessage());
                saveButton.setDisable(false);
            }
        });
    }

    public void back(ActionEvent event) {
        FxNavigation.navigateTo("hello-view");
    }
}
