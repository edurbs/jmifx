package com.jmifx;

/**
 * SPI for the platform HTTP transport used by {@link FxHttp}. Selected via
 * {@code ServiceLoader} (build-time-processed by TeaVM): the browser/wasm
 * transport ships in this module's {@code META-INF/services}; JVM transports
 * register their own provider (test fixture: {@code JvmHttpTransport}).
 *
 * <p>Implementations deliver results on any thread — the {@link FxHttp} facade
 * marshals every listener callback to the FX application thread.
 */
public interface FxHttpTransport {

    /**
     * POSTs {@code body} with the given content type and an optional
     * {@code Authorization} header value (already fully formed, e.g.
     * {@code Basic …} or {@code Bearer …}).
     */
    void post(String url, String contentType, String authorization, String body,
              FxHttp.Listener listener);
}
