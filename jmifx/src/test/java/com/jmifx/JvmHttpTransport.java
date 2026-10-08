package com.jmifx;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * JVM transport for FxHttp — test/desktop fixture, NOT compiled to wasm (the
 * test source set never reaches the TeaVM classpath). Delivers results on the
 * HttpClient thread; the FxHttp facade marshals callbacks to the FX thread.
 */
class JvmHttpTransport implements FxHttpTransport {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public void post(String url, String contentType, String authorization, String body,
                     FxHttp.Listener listener) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(FxHttp.resolveUrl(url)))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        listener.onFailure(error.getCause() != null ? error.getCause() : error);
                    } else {
                        listener.onResult(response.statusCode(), response.body());
                    }
                });
    }
}
