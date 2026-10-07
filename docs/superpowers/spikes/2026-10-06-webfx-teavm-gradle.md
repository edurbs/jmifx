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
