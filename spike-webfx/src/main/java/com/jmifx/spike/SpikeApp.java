package com.jmifx.spike;

import dev.webfx.kit.launcher.WebFxKitLauncher;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SpikeApp extends Application {

    private final Label label = new Label("Hello!");
    private final TextField field = new TextField();

    /**
     * fetch() bridge with method/content-type/authorization/body — the whole promise
     * chain stays in JS and calls back into Java functors with primitives/strings
     * only (the candidate recipe for the jmifx FxHttp wasm transport).
     */
    @JSFunctor
    interface TextCallback extends JSObject {
        void onResult(int status, String body);
    }

    @JSFunctor
    interface ErrorCallback extends JSObject {
        void onError(String message);
    }

    @JSBody(params = {"url", "method", "contentType", "authorization", "body", "onOk", "onErr"},
            script = "var h = {};"
                   + "if (contentType) h['Content-Type'] = contentType;"
                   + "if (authorization) h['Authorization'] = authorization;"
                   + "fetch(url, {method: method, headers: h, body: body})"
                   + "  .then(function(r) { return r.text().then(function(t) { onOk(r.status, t); }); })"
                   + "  .catch(function(e) { onErr('' + e); });")
    static native void fetchText(String url, String method, String contentType, String authorization,
                                 String body, TextCallback onOk, ErrorCallback onErr);

    @JSBody(params = {"o"}, script = "return '' + o;")
    static native String jsToString(Object o);

    @Override
    public void start(Stage stage) {
        Button button = new Button("Greet");
        button.setOnAction(e ->
                label.setText(field.getText().isBlank() ? "Hello!" : "Hello, " + field.getText() + "!"));

        Button probe = new Button("JRE probe");
        probe.setOnAction(e -> runJreProbe());

        Button httpProbe = new Button("POST probe");
        httpProbe.setOnAction(e -> runHttpProbe());

        Button slProbe = new Button("SL probe");
        slProbe.setOnAction(e -> runServiceLoaderProbe());

        VBox root = new VBox(10, label, field, button, probe, httpProbe, slProbe);
        stage.setScene(new Scene(root, 400, 340));
        stage.setTitle("jmifx spike");
        stage.show();
    }

    /** Probe 1: System property + Base64 under TeaVM. */
    private void runJreProbe() {
        String prop = "<threw>";
        try {
            prop = System.getProperty("jmifx.spike", "default");
        } catch (Throwable t) {
            prop = "<threw: " + t + ">";
        }
        String b64 = "<threw>";
        try {
            b64 = Base64.getEncoder().encodeToString("probe".getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            b64 = "<threw: " + t + ">";
        }
        label.setText("prop=" + prop + " b64=" + b64);
    }

    /** Probe 2: POST + JSON body + status + async callback into the UI, via fetch. */
    private void runHttpProbe() {
        label.setText("HTTP …");
        fetchText("/echo", "POST", "application/json", null, "{\"ping\":\"pong\"}",
                (status, body) -> label.setText("HTTP " + status + ": " + body),
                message -> label.setText("HTTP <failed: " + message + ">"));
    }

    /** Probe 3: ServiceLoader provider discovery under TeaVM. */
    private void runServiceLoaderProbe() {
        try {
            String result = "SL: none found";
            for (SpikeService s : java.util.ServiceLoader.load(SpikeService.class)) {
                result = "SL found: " + s.name();
            }
            label.setText(result);
        } catch (Throwable t) {
            label.setText("SL <threw: " + t + ">");
        }
    }

    public static void main(String[] args) {
        // WebFX requirement: launch via WebFxKitLauncher, not Application.launch()
        WebFxKitLauncher.launchApplication(SpikeApp::new, args);
    }
}
