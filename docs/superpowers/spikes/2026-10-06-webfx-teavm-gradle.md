# Spike: WebFX Kit + TeaVM wasmGC on Gradle — 2026-10-06/07

**Verdict: Approach A confirmed.** A JavaFX app (Label + TextField + Button) compiled to WebAssembly by TeaVM's Gradle plugin + WebFX Kit snapshots, built by Gradle 9.5.1, verified interactive in a real browser (fill field → click Greet → label shows `Hello, Eduardo!`). Screenshot: `docs/superpowers/e2e/spike.png`.

## Working recipe (spike-webfx/build.gradle)

- **Gradle**: 9.5.1, plugin `org.teavm` version **0.14.1** (from Gradle Plugin Portal; compatible with Gradle 9.5.1 — one deprecation warning, no failures).
- **TeaVM DSL** (0.14.1):
  ```groovy
  teavm {
      all { mainClass = 'com.jmifx.spike.SpikeApp' }
      wasmGC { addedToWebApp = true; targetFileName = 'spike.wasm' }
  }
  ```
  Output lands in `build/generated/teavm/wasm-gc/` (`spike.wasm` + `spike.wasm-runtime.js`). The wasm build task is **`buildWasmGC`** (not wired into `assemble` without the war plugin — the plugin/tasks must depend on it explicitly).
- **`--release 21`** for module bytecode (JDK 25 runs Gradle).
- **Compile against WebFX Kit emul artifacts, NOT `org.openjfx`**: Gradle ignores the Maven profiles that pull platform-classified openjfx jars, so `org.openjfx:javafx-controls` resolves to empty jars. The kit emul jars provide the same `javafx.*` API and are exactly what TeaVM compiles — better API parity.

## Exact dependency set (dev.webfx:0.1.0-SNAPSHOT, repo https://central.sonatype.com/repository/maven-snapshots/)

- `webfx-kit-javafxgraphics-elemental2` (pulls base/graphics emul + peers transitively)
- `webfx-kit-javafxgraphics-registry-elemental2` — **required**: provides `JavaFxGraphicsRegistry` used by `Region.<clinit>`; without it the wasm fails at runtime class-init
- `webfx-kit-javafxcontrols-emul` — Label/TextField/Button classes
- `webfx-kit-javafxcontrols-registry-elemental2` — **required**: `JavaFxControlsRegistry` used by every control's `<clinit>`
- `webfx-platform-teavm-elemental2-polyfill` — **required**: elemental2 bindings support under TeaVM ("Field elemental2.dom.DomGlobal.document was not found" without it)
- `webfx-platform-boot-java`, `webfx-platform-console-elemental2`, `webfx-platform-os-elemental2`, `webfx-platform-resource-teavm`, `webfx-platform-resource-web`, `webfx-platform-shutdown-elemental2`, `webfx-platform-storage-elemental2`, `webfx-platform-uischeduler-elemental2`, `webfx-platform-useragent-elemental2` (platform services; from the official teavm-wasm demo pom)

## Required launch idiom

`javafx.application.Application.launch()` is stubbed in the kit and reports *"Please use FxKitLauncher.launchApplication() and not Application.launch()"* ( TeaVM surfaces it during its build-time metadata execution, failing the build). Correct entry point:

```java
import dev.webfx.kit.launcher.WebFxKitLauncher;
public static void main(String[] args) { WebFxKitLauncher.launchApplication(SpikeApp::new, args); }
```

**Impact on the design:** `FxApplication` (client framework) must wrap `WebFxKitLauncher.launchApplication(App::new, args)` in its `main`, while keeping `extends javafx.application.Application` so `start(Stage)` still works.

## index.html loader pattern (works as-is)

```html
<script src="./spike.wasm-runtime.js"></script>
<script>
  async function main() {
    let teavm = await TeaVM.wasmGC.load("spike.wasm");
    teavm.exports.main([]);
  }
  main();
</script>
```

## Wasm size

1.5 MB unminified (ADVANCED optimization/minification not yet configured — acceptable for M1).

## Decisions for downstream tasks

- Task 8 (plugin): depend on `org.teavm:teavm-gradle-plugin:0.14.1`; run `buildWasmGC`; copy from `build/generated/teavm/wasm-gc/`.
- Task 3 (framework): `FxApplication` uses `WebFxKitLauncher.launchApplication` (also means jmifx lib needs the kit launcher on its compile classpath — add `dev.webfx:webfx-kit-launcher` as compileOnly dependency).
- All client modules: compile against kit emul jars (no org.openjfx anywhere).
- JVM unit tests of the client framework (Task 3) still need a JVM-runnable javafx provider on the test classpath: use `org.openjfx:javafx-base:25:linux`, `javafx-graphics:25:linux`, `javafx-controls:25:linux` (explicit **linux classifiers**).

## HTTP from wasm (2026-10-07) — M2 spike

**Verdict: route B (fetch via `@JSBody` bridge).** Screenshot: `docs/superpowers/e2e/spike-http.png`.

- **Route A (`java.net.HttpURLConnection`) is dead at runtime**: the TeaVM build succeeds, but the wasm module **traps during instantiation** ("dereferencing a null pointer") whenever `java.net` code is reachable from main (verified by bisect: build + load fine without it, trap with it linked behind a button handler). Never wire `java.net` into wasm-reachable code.
- **Route B recipe (working, verified in Chromium)** — one `@JSBody` does the *entire* fetch chain in JS and calls back into Java `@JSFunctor` interfaces with primitives/strings only:

```java
@JSFunctor interface TextCallback extends JSObject { void onResult(int status, String body); }
@JSFunctor interface ErrorCallback extends JSObject { void onError(String message); }

@JSBody(params = {"url", "method", "contentType", "authorization", "body", "onOk", "onErr"},
        script = "var h = {};"
               + "if (contentType) h['Content-Type'] = contentType;"
               + "if (authorization) h['Authorization'] = authorization;"
               + "fetch(url, {method: method, headers: h, body: body})"
               + "  .then(function(r) { return r.text().then(function(t) { onOk(r.status, t); }); })"
               + "  .catch(function(e) { onErr('' + e); });")
static native void fetchText(String url, String method, String contentType, String authorization,
                             String body, TextCallback onOk, ErrorCallback onErr);
```

- **elemental2 `Promise`/`Response` marshalling is a dead end**: `DomGlobal.window.fetch(...)` + Java-side `.then(...)` on `elemental2.promise.Promise` rejects with `Error: (could not fetch message)` (the request itself returns 200 — the failure is in the JSO marshalling/un-annotated `Response.text()`). `@JSProperty("status")` subinterfaces DO work, but the all-JS chain above is simpler and needs no elemental2 types in Java signatures at all.
- **JRE probes**: `System.getProperty(key, default)` returns the default without throwing ✓; `java.util.Base64.getEncoder()` produces correct output (`cHJvYmU=`) ✓.
- **`ServiceLoader` works via `iterator()`/for-each ✓** (provider found); **`findFirst()` is NOT implemented** in TeaVM 0.14.1 ("Method java.util.ServiceLoader.findFirst() was not found" — build failure).
- **No UI freeze** (callbacks arrive asynchronously); browser console clean.
- Probe server: `spike-webfx/echo-server.py` (same-origin static + POST /echo, port 8090).
