# jmifx — Jmix add-on for JavaFX/WebFX (Wasm) views

**Date:** 2026-10-06
**Status:** Approved design (brainstorming complete)
**Target:** Jmix 3.0.3, Spring Boot 3.x, Gradle, TeaVM 0.15+ (wasmGC), WebFX Kit (pinned snapshot)

## 1. Purpose

jmifx is a Jmix add-on that replaces Vaadin as the view layer for Jmix applications. Developers author views in **JavaFX** using **Scene Builder** (FXML files + standard JavaFX controllers). The add-on compiles those views — together with a small client-side view framework and the WebFX Kit — via **TeaVM to WebAssembly (wasm-gc)**, producing a self-contained client app served by the Jmix Spring Boot application. The Jmix server remains the backend (data and security integration in later milestones).

**Milestone 1 (this spec):** prove the full pipeline with one view containing exactly three components — a **Label**, a **TextField**, and a **Button** — rendered in the browser as Wasm, served by a Jmix 3.0.3 app.

## 2. Decisions (from brainstorming, approved)

| Decision | Choice | Rationale |
|---|---|---|
| Client-server topology | **Full replacement** | The WebFX app *is* the UI, served by the Jmix app (like erp's offline-SPA packaging). Cleanest "instead of Vaadin" story; no hybrid embedding. |
| Milestone 1 scope | **Pipeline PoC** | FXML → codegen → TeaVM → Wasm → served by Jmix at `/fx/`. Button action is client-side only. No REST/auth. |
| Jmix version | **3.0.3** | Matches the erp app so it can adopt the add-on immediately. |
| FXML strategy | **Approach A — build-time codegen** | WebFX's `FXMLLoader` cannot work under TeaVM (reflection). WebFX's own recommended path is FXML → transpilable Java (proven by their Memory Game prototype). Codegen gives compile-time safety. |
| Build integration | **TeaVM Gradle plugin + WebFX Kit artifacts**, spike-first | Pure-Gradle path fits Jmix add-on conventions. Time-boxed spike validates the WebFX Kit dependency set; fallback = Approach C (invoke WebFX CLI/Maven from Gradle) without changing the rest of the design. |
| Wasm targets | **wasm-gc only** for M1 | Supported by all modern browsers (Chrome, Edge, Firefox, Safari recent versions). JS fallback deferred. |

## 3. Hard constraint driving the architecture

TeaVM-compiled code cannot use Spring or Jmix server classes. It compiles against the WebFX Kit's `javafx.*` API and a Java subset. Therefore the add-on is split along a **client/server line**: everything compiled to Wasm lives in client modules with zero Spring/Jmix dependencies; everything Spring lives on the server side.

## 4. Project structure

```
jmifx/                          (Gradle multi-project, standalone repo)
├── jmifx/                      # CLIENT library: FxView, FxViewRegistry, FxNavigator,
│                               #   FxApplication. Pure JavaFX API (no Spring). Compiled into
│                               #   the Wasm app; also runnable on desktop JVM via WebFX Kit
│                               #   JRE variant (free desktop preview).
├── jmifx-codegen/              # JVM library: parses .fxml, emits Java view-builder source.
│                               #   Fails with file:line on anything unsupported.
├── jmifx-gradle/               # Gradle plugin: generateFxViews task, TeaVM wasmGC config,
│                               #   web-output packaging for the server.
├── jmifx-starter/              # SERVER: Spring Boot auto-config serving compiled assets /fx/**
└── jmifx-demo/                 # Example Jmix 3.0.3 app (milestone-1 deliverable)
    ├── ...                     #   standard Jmix app modules using jmifx-starter
    └── client/                 #   FXML views + controllers, built by the jmifx Gradle plugin
```

Developer workflow: put `hello-view.fxml` + `HelloViewController.java` into the app's client module (FXML authored/edited in Scene Builder), run the app, open `http://host/fx/`.

## 5. Client view framework (`jmifx`)

Borrowing Jmix's descriptor+controller mental model with the standard JavaFX idiom:

- **`FxView`** — an interface: `getId()` and `getRoot()` returning `javafx.scene.Parent`. (No lifecycle hook in M1 — the JavaFX-standard `controller.initialize()` is the only initialization mechanism; add framework hooks when a milestone needs them.)
- **A view = one FXML file + one controller.** FXML declares `fx:controller`; controller uses `@FXML`-annotated fields and handler methods (`onAction="#handler"`). Scene Builder round-trips unchanged.
- **`FxViewRegistry`** — view name → factory, populated by the generated `FxViewsIndex` class.
- **`FxNavigator`** — swaps view roots in the main scene's content pane. M1: startup view only; URL routing deferred.
- **`FxApplication`** — entry point: creates Stage/Scene, resolves startup view, boots registry. Apps subclass it and set the startup view id.

### Milestone-1 FXML subset (enforced by codegen)

- Containers: `VBox`, `HBox`, `StackPane`, `Pane`
- Controls: `Label`, `TextField`, `Button`
- Properties: `fx:id`, `id`, `text`, `promptText`, `prefWidth`, `prefHeight`, `maxWidth`, `spacing`, `alignment`
- Event handlers: `onAction="#method"` (`ActionEvent`)
- Controller: `fx:controller` FQCN, `@FXML` package-visible fields of supported types, optional `@FXML initialize()`

Explicitly **out** of M1: `fx:include`, `fx:define`, `fx:copy`, `fx:root`, expression bindings (`${...}`), resource bundles (`%key`), CSS files, all other controls/properties.

## 6. Codegen contract (`jmifx-codegen`)

For each `src/main/fxml/**/*.fxml` in a client module, generate one class in the controller's package:

```java
// Generated from hello-view.fxml — DO NOT EDIT
public class HelloView implements FxView {
    private final HelloViewController controller = new HelloViewController();
    private final VBox root = new VBox(10);           // spacing from FXML
    private final Label greetingLabel = new Label();  // one field per fx:id
    private final TextField nameField = new TextField();
    private final Button greetButton = new Button();

    public HelloView() {
        greetingLabel.setText("Hello");
        greetButton.setText("Greet");
        greetButton.addEventHandler(ActionEvent.ACTION, controller::greet);
        controller.greetingLabel = greetingLabel;     // @FXML injection, direct assignment
        controller.nameField = nameField;
        controller.initialize();                      // only if declared
    }
    @Override public Parent getRoot() { return root; }
}
```

Rules:

- **Validation is compile-time safety**: unknown tag, unknown property, bad handler signature, missing controller class, private `@FXML` field → build failure with FXML file + line number.
- **`@FXML` fields must be package-private** (Scene Builder's default is private). Generated classes live in the same package and assign fields directly — zero reflection anywhere. This is the single documented deviation from vanilla JavaFX.
- A generated **`FxViewsIndex`** registers every view (derived name → factory).
- Implementation: JDK DOM parsing + a plain Java source writer; golden-file unit tests. No JavaPoet dependency for M1.
- View class naming: `hello-view.fxml` → `HelloView` (kebab → Pascal + `View` suffix), configurable later if needed.

## 7. Build integration (`jmifx-gradle`)

**Spike (first implementation task, time-boxed):** a bare Gradle module rendering one `Label` in a browser using the `org.teavm` Gradle plugin (0.15+, `wasmGC` target) + WebFX Kit snapshot artifacts. Discover the exact WebFX artifact set by translating what the WebFX CLI generates for TeaVM apps (read its generated poms). **If unmaintainable → fallback: Approach C** — client module built by WebFX CLI/Maven invoked from Gradle; the rest of this design is unaffected.

The jmifx Gradle plugin, applied to a client module, wires:

1. **`generateFxViews`** — runs jmifx-codegen on `src/main/fxml`, adds generated sources to the compile source set.
2. **TeaVM `wasmGC` compilation** — `mainClass` = the app's `FxApplication` subclass; WebFX Kit pinned to an exact snapshot version (incubation churn control); client module compiles with a TeaVM-compatible `--release` (expected 21; confirmed by spike — local JDK 25 can build it).
3. **`jmifxWeb` output directory** — `.wasm` + wasm-runtime JS + templated `index.html` (based on WebFX's TeaVM loader pattern), ready for static packaging.

## 8. Server side (`jmifx-starter`)

Spring Boot auto-configuration:

- Serves the packaged web output (classpath `jmifx-web/**`) at **`/fx/**`** via resource-handler registry.
- Assets land in a **dedicated output directory** and are packaged from there — never written into `build/resources/main` (erp's offline-SPA lesson: that caused recurring build incidents).
- Properties: `jmifx.enabled` (default `true`), `jmifx.path` (default `/fx`).
- M1: no auth, no endpoints. Later milestones add REST data (jmix-restds) and shared auth.

## 9. Milestone-1 deliverable: `jmifx-demo`

Minimal Jmix 3.0.3 app + `client` module with exactly one view:

- `hello-view.fxml` — VBox with **Label** ("Hello!"), **TextField** (prompt "Type your name"), **Button** ("Greet")
- `HelloViewController` — `greet()` copies the field value into the label text

**Acceptance:** `./gradlew :jmifx-demo:bootRun` → open `http://localhost:8080/fx/` → the view renders as WebAssembly; typing a name and clicking Greet updates the label. Verified in a real browser.

## 10. Testing strategy

| Layer | Approach |
|---|---|
| `jmifx-codegen` | Unit tests: FXML fixtures → golden-file generated-source assertions; error fixtures (unknown tag, private field, bad handler, missing controller) → assert build-failure messages with file:line |
| Generated code | Integration test compiles generated sources via JDK compiler API, instantiates views, asserts scene-graph structure |
| Client framework | JUnit on desktop JVM (WebFX Kit JRE variant): construct views, assert state |
| E2E | Manual browser verification of the demo acceptance flow (automation deferred) |

Error handling: codegen problems = build failures with file:line; client runtime errors = browser console + simple on-screen overlay (never a silent blank page).

## 11. Risks

1. **WebFX Kit artifact assembly on Gradle/TeaVM** — mitigated by the time-boxed spike; Approach-C fallback documented.
2. **WebFX incubation churn** — pin exact snapshot; breaking changes possible until 1.0.
3. **TeaVM ↔ WebFX version alignment** — resolved during spike; versions pinned together.
4. **wasm-gc browser requirement** — modern browsers only; acceptable for M1; JS fallback later.
5. **Package-private `@FXML` convention** — documented deviation; codegen enforces with clear error.

## 12. Deferred scope (post-M1, not in this spec's implementation)

REST data access (jmix-restds), auth/login, URL routing/navigation, i18n resource bundles, styling/CSS story, additional controls, JS fallback target, hot-reload dev mode, Jmix Studio wizard integration, publishing to Jmix marketplace, running the same views on desktop/mobile via WebFX's multi-platform output.
