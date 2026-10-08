package com.jmifx;

import javafx.application.Platform;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ServiceLoader;

/**
 * Dual-platform async HTTP for jmifx clients (JVM and browser/wasm). Zero
 * reflection, zero Spring/Jmix.
 *
 * <ul>
 *   <li>{@link #post} sends JSON and auto-attaches {@code Authorization: Bearer …}
 *       when {@link FxAuth} holds a token.</li>
 *   <li>{@link #postForm} sends {@code application/x-www-form-urlencoded} with an
 *       optional explicit {@code Authorization} (e.g. client Basic auth for the
 *       OAuth token endpoint) and never auto-attaches a Bearer token.</li>
 * </ul>
 *
 * <p>Relative URLs are resolved against {@code jmifx.base.url}
 * (default {@code http://localhost:8080} — same-origin for the demo). Listener
 * callbacks are always marshaled to the FX application thread.
 */
public final class FxHttp {

    /** Receives the outcome of a request; invoked on the FX application thread. */
    public interface Listener {
        /** Any HTTP response — 2xx, 4xx, 5xx alike. */
        void onResult(int statusCode, String body);

        /** Network-level failure (unreachable server, timeout). */
        void onFailure(Throwable t);
    }

    static final String DEFAULT_BASE = "http://localhost:8080";

    private static volatile FxHttpTransport transport;

    private FxHttp() {
    }

    /** Test seam — pins the transport instead of ServiceLoader discovery. */
    static void setTransport(FxHttpTransport fixed) {
        transport = fixed;
    }

    public static void post(String url, String jsonBody, Listener listener) {
        String token = FxAuth.getAccessToken();
        transport().post(url, "application/json",
                token == null ? null : "Bearer " + token, jsonBody, wrap(listener));
    }

    public static void postForm(String url, String formBody, String basicAuth, Listener listener) {
        transport().post(url, "application/x-www-form-urlencoded",
                basicAuth, formBody, wrap(listener));
    }

    /** {@code "Basic " + Base64(user:pass)} — value for the Authorization header. */
    public static String basic(String username, String password) {
        String raw = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** UTF-8 percent-encoding for form bodies and query values; space → {@code %20}. */
    public static String urlEncode(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
                    || c == '-' || c == '.' || c == '_' || c == '~') {
                out.append((char) c);
            } else {
                out.append('%').append(String.format("%02X", c));
            }
        }
        return out.toString();
    }

    /**
     * Absolute URLs pass through; relative URLs get the configured base (single {@code /} join).
     * JVM/desktop transports call this — the browser transport does NOT (fetch resolves
     * relative URLs natively against the page origin, which keeps the client working
     * when the app is served from any host).
     */
    static String resolveUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return baseUrl().replaceAll("/+$", "") + "/" + url.replaceAll("^/+", "");
    }

    private static String baseUrl() {
        try {
            return System.getProperty("jmifx.base.url", DEFAULT_BASE);
        } catch (Throwable t) {
            return DEFAULT_BASE; // TeaVM may not implement system properties
        }
    }

    private static FxHttpTransport transport() {
        FxHttpTransport t = transport;
        if (t != null) {
            return t;
        }
        for (FxHttpTransport discovered : ServiceLoader.load(FxHttpTransport.class)) {
            transport = discovered;
            return discovered;
        }
        throw new IllegalStateException(
                "no FxHttpTransport on the classpath (browser: META-INF/services; JVM: register one)");
    }

    private static FxHttp.Listener wrap(Listener listener) {
        return new Listener() {
            @Override
            public void onResult(int statusCode, String body) {
                Platform.runLater(() -> listener.onResult(statusCode, body));
            }

            @Override
            public void onFailure(Throwable t) {
                Platform.runLater(() -> listener.onFailure(t));
            }
        };
    }
}
