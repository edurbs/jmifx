package com.jmifx;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Browser/wasm transport — the spike-proven fetch bridge (see
 * {@code docs/superpowers/spikes/2026-10-06-webfx-teavm-gradle.md}, "HTTP from
 * wasm"): the entire fetch chain runs in one JS snippet and calls back into
 * Java functors with primitives/strings only. Chaining elemental2
 * {@code Promise}/{@code Response} objects in Java rejects with wrapped errors
 * — do not "simplify" this into Java-side promise chaining.
 */
final class BrowserHttpTransport implements FxHttpTransport {

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

    @Override
    public void post(String url, String contentType, String authorization, String body,
                     FxHttp.Listener listener) {
        fetchText(url, "POST", contentType, authorization, body,
                (status, responseBody) -> listener.onResult(status, responseBody),
                message -> listener.onFailure(new IllegalStateException(message)));
    }
}
