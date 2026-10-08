package com.jmifx.demo.client;

import com.jmifx.FxAuth;
import com.jmifx.FxHttp;
import com.jmifx.FxJson;
import com.jmifx.FxNavigation;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller for login-view.fxml — Jmix-login-style sign-in over the OAuth2
 * password grant. Fields are package-visible (jmifx convention).
 */
public class LoginViewController {

    Label titleLabel;
    TextField usernameField;
    PasswordField passwordField;
    Button loginButton;
    Label statusLabel;

    /** OAuth client registered in the demo server's application.yml. */
    private static final String BASIC_CLIENT_AUTH = FxHttp.basic("jmifx", "jmifx-secret");

    public void initialize() {
    }

    public void login(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            statusLabel.setText("Username and password required");
            return;
        }
        loginButton.setDisable(true);
        statusLabel.setText("Signing in…");
        String form = "grant_type=password&username=" + FxHttp.urlEncode(username.trim())
                + "&password=" + FxHttp.urlEncode(password);
        FxHttp.postForm("/oauth2/token", form, BASIC_CLIENT_AUTH, new FxHttp.Listener() {
            @Override
            public void onResult(int statusCode, String body) {
                if (statusCode >= 200 && statusCode < 300) {
                    FxAuth.setAccessToken(FxJson.stringValue(body, "access_token"));
                    FxNavigation.navigateTo("hello-view");
                } else {
                    statusLabel.setText("Login failed (HTTP " + statusCode + ")");
                }
                loginButton.setDisable(false);
            }

            @Override
            public void onFailure(Throwable t) {
                statusLabel.setText("Login failed: " + t.getMessage());
                loginButton.setDisable(false);
            }
        });
    }
}
