package com.jmifx;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FxHttpTest {

    /** What the probe server observed for the last request. */
    static final AtomicReference<String> capturedAuthorization = new AtomicReference<>();
    static final AtomicReference<String> capturedContentType = new AtomicReference<>();
    static final AtomicReference<String> capturedBody = new AtomicReference<>();

    static HttpServer server;
    static String baseUrl;

    @BeforeAll
    static void startServerAndTransport() throws Exception {
        FxTestKit.start();
        FxHttp.setTransport(new JvmHttpTransport());

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/echo", FxHttpTest::handleEcho);
        server.createContext("/error", exchange -> respond(exchange, 500, "boom"));
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @AfterEach
    void clearAuth() {
        FxAuth.clear();
        System.clearProperty("jmifx.base.url");
    }

    private static void handleEcho(HttpExchange exchange) throws java.io.IOException {
        capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200, "echo:" + capturedBody.get());
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    void postRoundTripsJsonBody() throws Exception {
        String json = "{\"name\":\"Berlin\"}";
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> body = new AtomicReference<>();
        AtomicInteger status = new AtomicInteger(-1);

        FxHttp.post(baseUrl + "/echo", json, new FxHttp.Listener() {
            @Override public void onResult(int statusCode, String responseBody) {
                status.set(statusCode);
                body.set(responseBody);
                done.countDown();
            }
            @Override public void onFailure(Throwable t) { done.countDown(); }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(200, status.get());
        assertEquals("echo:" + json, body.get());
        assertEquals(json, capturedBody.get());
        assertEquals("application/json", capturedContentType.get());
        assertNull(capturedAuthorization.get());
    }

    @Test
    void serverErrorSurfacesStatusCode() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger status = new AtomicInteger(-1);
        AtomicBoolean failed = new AtomicBoolean(false);

        FxHttp.post(baseUrl + "/error", "{}", new FxHttp.Listener() {
            @Override public void onResult(int statusCode, String responseBody) {
                status.set(statusCode);
                done.countDown();
            }
            @Override public void onFailure(Throwable t) { failed.set(true); done.countDown(); }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(500, status.get());
        assertFalse(failed.get(), "5xx is a result, not a failure");
    }

    @Test
    void unreachableServerCallsOnFailure() throws Exception {
        // non-routable address: deterministic connect timeout (this box never
        // fast-refuses dead local ports — see ledger)
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean resulted = new AtomicBoolean(false);

        FxHttp.post("http://10.255.255.1:81/echo", "{}", new FxHttp.Listener() {
            @Override public void onResult(int statusCode, String responseBody) {
                resulted.set(true);
                done.countDown();
            }
            @Override public void onFailure(Throwable t) { failure.set(t); done.countDown(); }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertNotNull(failure.get(), "connect timeout must reach onFailure");
        assertFalse(resulted.get());
    }

    @Test
    void listenerRunsOnFxApplicationThread() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean onFxThread = new AtomicBoolean(false);

        FxHttp.post(baseUrl + "/echo", "{}", new FxHttp.Listener() {
            @Override public void onResult(int statusCode, String responseBody) {
                onFxThread.set(javafx.application.Platform.isFxApplicationThread());
                done.countDown();
            }
            @Override public void onFailure(Throwable t) { done.countDown(); }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue(onFxThread.get(), "listener must be marshaled to the FX application thread");
    }

    @Test
    void postFormSendsBasicAuthAndFormBody() throws Exception {
        String form = "grant_type=password&username=admin&password=admin";
        CountDownLatch done = new CountDownLatch(1);

        FxHttp.postForm(baseUrl + "/echo", form, FxHttp.basic("u", "p"), new FxHttp.Listener() {
            @Override public void onResult(int statusCode, String responseBody) { done.countDown(); }
            @Override public void onFailure(Throwable t) { done.countDown(); }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(FxHttp.basic("u", "p"), capturedAuthorization.get());
        assertEquals("application/x-www-form-urlencoded", capturedContentType.get());
        assertEquals(form, capturedBody.get());
    }

    @Test
    void bearerAttachedOnlyWhenTokenSet() throws Exception {
        FxAuth.setAccessToken("tok");
        CountDownLatch withToken = new CountDownLatch(1);
        FxHttp.post(baseUrl + "/echo", "{}", resultListener(withToken));
        assertTrue(withToken.await(10, TimeUnit.SECONDS));
        assertEquals("Bearer tok", capturedAuthorization.get());

        FxAuth.clear();
        CountDownLatch withoutToken = new CountDownLatch(1);
        FxHttp.post(baseUrl + "/echo", "{}", resultListener(withoutToken));
        assertTrue(withoutToken.await(10, TimeUnit.SECONDS));
        assertNull(capturedAuthorization.get());
    }

    @Test
    void resolveUrlCases() {
        assertEquals("http://example.com/x", FxHttp.resolveUrl("http://example.com/x"));
        assertEquals("https://example.com", FxHttp.resolveUrl("https://example.com"));
        System.clearProperty("jmifx.base.url");
        assertEquals("http://localhost:8080/rest/entities/City",
                FxHttp.resolveUrl("/rest/entities/City"));
        System.setProperty("jmifx.base.url", "http://other:9090/");
        assertEquals("http://other:9090/rest/entities/City",
                FxHttp.resolveUrl("/rest/entities/City"));
    }

    @Test
    void urlEncodePinsKnownValues() {
        assertEquals("S%C3%A3o%20Paulo", FxHttp.urlEncode("São Paulo"));
        assertEquals("grant_type%3Dpassword", FxHttp.urlEncode("grant_type=password"));
        assertEquals("", FxHttp.urlEncode(""));
        assertEquals("abcXYZ019-._~", FxHttp.urlEncode("abcXYZ019-._~"));
    }

    @Test
    void basicMatchesJdkBase64() {
        assertEquals("Basic " + Base64.getEncoder()
                        .encodeToString("jmifx:jmifx-secret".getBytes(StandardCharsets.UTF_8)),
                FxHttp.basic("jmifx", "jmifx-secret"));
    }

    private static FxHttp.Listener resultListener(CountDownLatch done) {
        return new FxHttp.Listener() {
            @Override public void onResult(int statusCode, String responseBody) { done.countDown(); }
            @Override public void onFailure(Throwable t) { done.countDown(); }
        };
    }
}
