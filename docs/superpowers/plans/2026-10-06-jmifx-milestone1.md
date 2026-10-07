# jmifx Milestone 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the jmifx pipeline — a Scene Builder FXML view (Label + TextField + Button) with a plain JavaFX controller, compiled by TeaVM to WebAssembly via the WebFX Kit, served by a Jmix 3.0.3 app at `/fx/`, working in a real browser.

**Architecture:** Build-time codegen turns FXML into reflection-free Java view classes (generated classes live in the controller's package and assign package-visible `@FXML` fields directly). A small client framework (`FxView`/registry/navigator) plus the generated views compile against the WebFX Kit through the TeaVM Gradle plugin (`wasmGC`). A Spring Boot starter serves the packaged Wasm assets from the classpath. Approach-A fallback (spec §7): if the spike fails, switch the client build to the WebFX CLI/Maven bridge — the rest of this plan is unaffected.

**Tech Stack:** Gradle 9.5.1, Java (release 21, JDK 25 toolchain locally), TeaVM Gradle plugin 0.14.1, WebFX Kit 0.1.0-SNAPSHOT, Jmix 3.0.3 / Spring Boot 4.1.x, JUnit 5, Gradle TestKit.

**Spec:** `docs/superpowers/specs/2026-10-06-jmifx-design.md` — the plan argues from the spec; executors read both.

## Global Constraints

- Group `com.jmifx`, version `0.1.0-SNAPSHOT`, plugin id `jmifx`.
- All modules compile with `options.release = 21`; Gradle runs on the local JDK 25.
- WebFX artifacts: group `dev.webfx`, version `0.1.0-SNAPSHOT`, snapshot repo `https://central.sonatype.com/repository/maven-snapshots/` (releases disabled, snapshots enabled).
- TeaVM Gradle plugin `org.teavm` version **0.14.1** (matches `webfx-parent`'s `teavm.version` — do not bump independently).
- Repositories everywhere: `mavenCentral()`, WebFX snapshot repo, `https://global.repo.jmix.io/repository/public`.
- Demo app: `io.jmix` Gradle plugin 3.0.3; the Spring Boot plugin is applied without a version (provided by the Jmix plugin classpath, same as erp). Jmix/Spring Boot 4.1.x line.
- OpenJFX (`org.openjfx:javafx-controls:25`) is **compileOnly + testOnly** in client modules — never on the TeaVM/Wasm classpath; the WebFX Kit provides `javafx.*` classes there.
- M1 FXML subset (spec §5), enforced by codegen: elements `VBox, HBox, StackPane, Pane, Label, TextField, Button`; attributes `fx:id, id, text, promptText, prefWidth, prefHeight, maxWidth, spacing, alignment, onAction`; handler `onAction="#method"` on Button only; `fx:controller` (FQCN) required. Controllers must declare `void initialize()` (package-visible at minimum) — generated code calls it; Scene Builder's default template already emits it.
- Controller-member errors (missing/private `@FXML` field, bad handler signature) surface as javac errors on the generated wiring lines; structural FXML errors surface as codegen build failures with `file:line`.
- Client/Wasm code path contains **zero reflection** and zero Spring/Jmix imports.
- Assets are served from classpath `jmifx-web/**` at `/fx/**`; assets are packaged via an **additional resources srcDir** (`build/jmifx-web`) — nothing may write into `build/resources/main`.
- M1 target is `wasmGC` only; modern browsers required.

## Review Focus

1. **Unsupported element in FXML** (e.g. `TableView`) → build fails with `file:line` naming the supported set — tested in Task 4 (`rejectsUnsupportedElement`).
2. **Unsupported attribute** (e.g. `style`) → build fails with `file:line` — tested in Task 4 (`rejectsUnsupportedAttribute`).
3. **Missing `fx:controller`** → build fails with `file:line` — tested in Task 4 (`rejectsMissingController`).
4. **Duplicate view ids** (two `.fxml` files deriving the same id) → build fails naming both files — tested in Task 4 (`rejectsDuplicateViewIds`).
5. **Starter with no assets on the classpath** (or `jmifx.enabled=false`) → context still boots, `/fx/**` returns 404 rather than crashing — tested in Task 9 (`serves404WhenAssetsMissing`, `disabledPropertyDisablesServing`).

## File Structure

```
jmifx/                                 ← root Gradle build
├── settings.gradle                    ← pluginManagement includeBuild('jmifx-gradle'); 4 modules
├── build.gradle                       ← group/version, release 21, JUnit for all subprojects
├── jmifx/                             ← client framework (compileOnly javafx-controls)
├── jmifx-starter/                     ← Spring Boot auto-config (Boot 4.1.x)
├── jmifx-demo/                        ← Jmix 3.0.3 app (H2 in-memory)
├── jmifx-demo-client/                 ← demo client module (plugin 'jmifx': FXML + controller + DemoFxApp)
├── jmifx-gradle/                      ← SEPARATE Gradle build (composite), own settings.gradle
│   ├── codegen/                       ← jmifx-codegen library (parser + emitters)
│   └── plugin/                        ← jmifx Gradle plugin (id 'jmifx')
├── spike-webfx/                       ← throwaway isolated build (Task 2; not in settings.gradle)
└── docs/superpowers/                  ← spec, plan, spikes/, e2e/
```

Note one deviation from spec §4's tree: `jmifx-codegen` lives **inside** the `jmifx-gradle` composite build — an included build cannot consume root-build `project()` dependencies, and the plugin needs codegen on its classpath.

---

### Task 1: Repo and build skeleton

**Files:**
- Create: `settings.gradle`, `build.gradle`, `.gitignore`
- Create: `gradle/wrapper/` + `gradlew` + `gradlew.bat` (copied from `/home/eduardo/IdeaProjects/erp/`)
- Create: minimal `build.gradle` for `jmifx`, `jmifx-starter`, `jmifx-demo`, `jmifx-demo-client`
- Create: `jmifx-gradle/settings.gradle`, `jmifx-gradle/build.gradle`, `jmifx-gradle/codegen/build.gradle`, `jmifx-gradle/plugin/build.gradle` (stubs)

**Interfaces:**
- Produces: root build with modules `jmifx`, `jmifx-starter`, `jmifx-demo`, `jmifx-demo-client`; included build `jmifx-gradle` with subprojects `codegen`, `plugin` (resolvable later as plugin id `jmifx` once Task 7 registers it — at this stage it is only a valid empty build).

- [ ] **Step 1: Copy the Gradle wrapper from erp**

```bash
cp -r /home/eduardo/IdeaProjects/erp/gradle/wrapper gradle/wrapper
cp /home/eduardo/IdeaProjects/erp/gradlew /home/eduardo/IdeaProjects/erp/gradlew.bat .
chmod +x gradlew
```
Wrapper is Gradle 9.5.1 bin distribution. Verify: `./gradlew --version` prints 9.5.1.

- [ ] **Step 2: Write `settings.gradle`**

```groovy
pluginManagement {
    includeBuild('jmifx-gradle')
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }
        maven { url = 'https://global.repo.jmix.io/repository/public' }
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }
        maven { url = 'https://global.repo.jmix.io/repository/public' }
    }
}
rootProject.name = 'jmifx'
include 'jmifx', 'jmifx-starter', 'jmifx-demo', 'jmifx-demo-client'
```

- [ ] **Step 3: Write root `build.gradle` and module stubs**

Root: `allprojects { group 'com.jmifx'; version '0.1.0-SNAPSHOT' }`; `subprojects { apply 'java'; java toolchain not pinned (local JDK 25); tasks.withType(JavaCompile) { options.release = 21; options.encoding = 'UTF-8' }; test { useJUnitPlatform() }; dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4' } }`. Each root module and each `jmifx-gradle` subproject gets a `build.gradle` containing only a comment placeholder (`// configured in its task`).

- [ ] **Step 4: Write `jmifx-gradle/settings.gradle`**

```groovy
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }
        gradlePluginPortal()
    }
}
rootProject.name = 'jmifx-gradle'
include 'codegen', 'plugin'
```

- [ ] **Step 5: Write `.gitignore`** (`.gradle/`, `build/`, `out/`, `*.class`, `.idea/`, `*.iml`, `node_modules/`)

- [ ] **Step 6: Verify builds resolve**

Run: `./gradlew projects` → expect the 4 root modules.
Run: `./gradlew -p jmifx-gradle projects` → expect `codegen`, `plugin`.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "build: repo skeleton with composite jmifx-gradle build"
```

---

### Task 2: Spike — WebFX Kit + TeaVM wasmGC on Gradle (decision gate)

**Files:**
- Create: `spike-webfx/settings.gradle`, `spike-webfx/build.gradle`, `spike-webfx/src/main/java/com/jmifx/spike/SpikeApp.java`, `spike-webfx/src/main/webapp/index.html`
- Create: `docs/superpowers/spikes/2026-10-06-webfx-teavm-gradle.md`

**Interfaces:**
- Produces: the spike findings document — exact working WebFX artifact list (including whichever `javafxcontrols` peer/registry artifacts Label/TextField/Button need beyond the list below), the TeaVM 0.14.1 Gradle DSL that works on Gradle 9.5.1, output file names, and the `--release` level. Tasks 3, 8 and 10 consume these values from the document.

Starting dependency set (validated recipe from WebFX's own `webfx-demo-tallycounter-application-teavm-wasm` pom — group `dev.webfx`, version `0.1.0-SNAPSHOT`): `webfx-kit-javafxgraphics-elemental2`, `webfx-platform-boot-java`, `webfx-platform-console-elemental2`, `webfx-platform-os-elemental2`, `webfx-platform-resource-teavm`, `webfx-platform-resource-web`, `webfx-platform-shutdown-elemental2`, `webfx-platform-storage-elemental2`, `webfx-platform-uischeduler-elemental2`, `webfx-platform-useragent-elemental2` — plus `compileOnly org.openjfx:javafx-controls:25` and whatever controls-peer artifacts the spike discovers are missing.

- [ ] **Step 1: Write the spike app**

`SpikeApp extends javafx.application.Application`: `start()` builds `VBox(label, textField, button)` — Label "Hello!", TextField, Button "Greet" whose `setOnAction` copies the field text into the label; `main()` calls `Application.launch(SpikeApp.class, args)`. `index.html` uses the TeaVM wasm loader pattern:

```html
<script type="text/javascript" src="./classes.wasm-runtime.js"></script>
<script>
  async function main() {
    let teavm = await TeaVM.wasmGC.load("classes.wasm");
    teavm.exports.main([]);
  }
  main();
</script>
```

- [ ] **Step 2: Build with TeaVM wasmGC**

`spike-webfx/build.gradle`: plugins `java` + `org.teavm` 0.14.1 (from plugin portal); `teavm { all { mainClass = 'com.jmifx.spike.SpikeApp' } wasmGC { /* targetDirectory per plugin docs */ } }`; `options.release = 21`. Run `./gradlew -p spike-webfx build`. Iterate: if Gradle 9.5.1 rejects the 0.14.1 plugin, try 0.15.0/0.16.0 and record which works with the kit; if controls are missing at runtime (blank/crash), add the kit's controls peer/registry artifacts until the three controls render.

- [ ] **Step 3: Verify in a real browser**

Serve the output dir (`python3 -m http.server 8899 -d <teavm-output-dir>` in background), open `http://localhost:8899/` with agent-browser, assert: label visible, typing into field + clicking Greet updates the label. Screenshot to `docs/superpowers/e2e/spike.png`. Kill the server.

- [ ] **Step 4: Write the findings document**

`docs/superpowers/spikes/2026-10-06-webfx-teavm-gradle.md` records: final dependency list with exact coordinates, TeaVM plugin version used + DSL block that worked, Gradle compatibility notes, `--release` level, output file names, browser verification result, and the explicit decision: **Approach A confirmed** or **fallback to Approach C** (WebFX CLI bridge — if fallback, STOP and re-plan Tasks 8/10 with the user).

- [ ] **Step 5: Commit**

```bash
git add spike-webfx docs/superpowers/spikes && git commit -m "spike: validate WebFX Kit + TeaVM wasmGC on Gradle 9.5.1"
```

---

### Task 3: jmifx client framework

**Files:**
- Create: `jmifx/build.gradle` (compileOnly + testRuntimeOnly `org.openjfx:javafx-controls:25`)
- Create: `jmifx/src/main/java/com/jmifx/FxView.java`, `FxViewRegistry.java`, `FxNavigator.java`, `FxApplication.java`
- Test: `jmifx/src/test/java/com/jmifx/FxViewRegistryTest.java`, `FxNavigatorTest.java`

**Interfaces:**
- Produces (consumed by generated code in Tasks 5–6, the plugin fixture in Task 7, and the demo in Task 10):

```java
public interface FxView {
    String getId();
    javafx.scene.Parent getRoot();
}

public final class FxViewRegistry {
    public void register(String id, Supplier<FxView> factory); // idempotent per distinct id, IllegalArgumentException on duplicate id with different factory
    public boolean hasView(String id);
    public FxView createView(String id);                       // IllegalArgumentException("No view registered for id 'x'") if unknown
    public java.util.Set<String> getViewIds();
}

public final class FxNavigator {
    public FxNavigator(javafx.scene.layout.Pane contentPane, FxViewRegistry registry);
    public void navigateTo(String viewId); // clears pane children, adds createView(id).getRoot(); on ANY Throwable during create/navigate installs a new Label("Failed to load view '<id>': <message>") and returns normally
}

public abstract class FxApplication extends javafx.application.Application {
    protected abstract String getStartupViewId();
    protected abstract void registerViews(FxViewRegistry registry);
    protected Scene createScene(Parent root) { return new Scene(root, 800, 600); }
    @Override public void start(Stage stage); // registry → registerViews, navigator on a StackPane root, navigateTo(startupViewId), show stage
}
```

- [ ] **Step 1: Write failing tests** — `FxViewRegistryTest`: register/hasView/createView round-trip; unknown id throws with exact message; duplicate id throws. `FxNavigatorTest` (use a plain `StackPane`, no Scene/toolkit): navigateTo installs view root as only child; unknown id → error Label containing `Failed to load view 'missing'`; factory throwing RuntimeException → error Label containing the exception message and pane still usable.
- [ ] **Step 2: Run tests — expect compile failure** (`FxView` etc. don't exist). Run: `./gradlew :jmifx:test`
- [ ] **Step 3: Implement the four classes** in `jmifx/src/main/java/com/jmifx/`. `start()` must guard navigation errors via the navigator (never a blank stage).
- [ ] **Step 4: Run tests — expect PASS.** Run: `./gradlew :jmifx:test`
- [ ] **Step 5: Commit** — `git add jmifx && git commit -m "feat: jmifx client framework (FxView, registry, navigator, application)"`

---

### Task 4: jmifx-codegen — FXML parsing and validation

**Files:**
- Create: `jmifx-gradle/codegen/build.gradle` (JUnit; no other deps)
- Create: `jmifx-gradle/codegen/src/main/java/com/jmifx/codegen/FxmlViewCompiler.java`, `CompilationResult.java`, `FxmlCompileError.java`, `parser/FxmlParser.java`, `model/ViewElement.java` (element tree + controller FQCN + view id)
- Test: `jmifx-gradle/codegen/src/test/java/com/jmifx/codegen/FxmlViewCompilerTest.java`
- Test fixtures: `jmifx-gradle/codegen/src/test/resources/fxml/valid-hello.fxml`, `unsupported-element.fxml`, `unsupported-attribute.fxml`, `missing-controller.fxml`, `duplicate-id-inner.fxml`, plus a second dir fixture for cross-file duplicates

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces (consumed by Task 5 emission, Task 7 plugin):

```java
public final class FxmlViewCompiler {
    /** Parses and validates; emits nothing yet when errors exist — errors are cumulative. */
    public CompilationResult compile(List<Path> fxmlFiles, Path generatedSourcesDir);
}
public record FxmlCompileError(Path file, int line, String message) {
    public String format(); // "<file>:<line>: <message>"
}
public record CompilationResult(List<FxmlCompileError> errors, List<ViewElement> views) {
    public boolean success();
}
```

View id = FXML file base name without extension (`hello-view.fxml` → `hello-view`); generated class name = PascalCase + `View` (`HelloView`). Parser uses the JDK DOM API with `LocationInfo`-style line numbers (a small SAX `Locator` pass or `com.sun.org.apache.xerces` parser with line info — pick one, record in code).

Validation rules (exact failure messages asserted in tests):
- Unknown element → `unsupported element 'X' (supported: VBox, HBox, StackPane, Pane, Label, TextField, Button)`
- Unknown attribute → `unsupported attribute 'X' on 'Y' (supported: fx:id, id, text, promptText, prefWidth, prefHeight, maxWidth, spacing, alignment, onAction)`
- Missing `fx:controller` → `fx:controller is required`
- Duplicate `fx:id` within one file → `duplicate fx:id 'X'`
- Duplicate view id across files → `duplicate view id 'X' in files <a> and <b>`
- `onAction` on non-Button → `onAction is only supported on Button`
- Non-numeric `prefWidth`/`prefHeight`/`maxWidth`/`spacing`, invalid `alignment` (must be a `javafx.geometry.Pos` constant name) → `invalid value 'V' for attribute 'A'`
- Root element must be a container → `root element must be one of VBox, HBox, StackPane, Pane`

- [ ] **Step 1: Write failing tests** — `valid-hello.fxml` parses with zero errors and view id `hello-view`; one test per error fixture asserting `format()` contains the exact message above and the fixture's real line number; cross-file duplicate test compiles two files and expects one error naming both paths.
- [ ] **Step 2: Run — expect FAIL** (classes missing). Run: `./gradlew -p jmifx-gradle :codegen:test`
- [ ] **Step 3: Implement** parser + model + compiler. Accumulate all errors (do not stop at the first).
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `git add jmifx-gradle/codegen && git commit -m "feat: fxml parser and validator with cumulative file:line errors"`

---

### Task 5: jmifx-codegen — Java source emission

**Files:**
- Create: `jmifx-gradle/codegen/src/main/java/com/jmifx/codegen/emit/ViewClassWriter.java`, `emit/ViewsIndexWriter.java`
- Modify: `FxmlViewCompiler` to write emitted sources into `generatedSourcesDir` when there are no errors
- Test: `jmifx-gradle/codegen/src/test/java/com/jmifx/codegen/EmissionTest.java` + golden files `src/test/resources/golden/HelloView.java.txt`, `FxViewsIndex.java.txt`
- Test fixture controller: `jmifx-gradle/codegen/src/test/java/fixture/HelloController.java` (package `fixture`; package-visible `@FXML Label outputLabel; TextField nameField; Button greetButton;` and `public void initialize()`)

**Interfaces:**
- Consumes: `ViewElement` model from Task 4.
- Produces: for `valid-hello.fxml` (fx:controller `fixture.HelloController`), emitted sources in the controller's package, exact content pinned by the golden files (write the golden files first, from the spec §6 skeleton). Emission rules: one field per `fx:id` in declaration order; static property setters from attributes (`text`/`promptText` → `setText`/`setPromptText`; `prefWidth`/`prefHeight`/`maxWidth` → `setPrefWidth(...)` etc. with double literals; `spacing` → constructor or setter per golden; `alignment` → `setAlignment(Pos.valueOf("..."))`); `greetButton.addEventHandler(javafx.event.ActionEvent.ACTION, controller::greet)` for `onAction="#greet"`; then `@FXML` field assignments in declaration order; then `controller.initialize()`; then `getRoot()`; `getId()` returns `"hello-view"`.

Also emits one `FxViewsIndex` per source package set? No — **one global `com.jmifx.generated.FxViewsIndex`** listing all views:

```java
package com.jmifx.generated;
public final class FxViewsIndex {
    public static void registerAll(com.jmifx.FxViewRegistry registry) {
        registry.register("hello-view", fixture.HelloView::new);
    }
}
```

- [ ] **Step 1: Write failing golden-file test** — compile `valid-hello.fxml`, read the two generated files, compare to golden (strip trailing whitespace per line). Golden files are written first, from the shapes above.
- [ ] **Step 2: Run — expect FAIL.** Run: `./gradlew -p jmifx-gradle :codegen:test`
- [ ] **Step 3: Implement** the two writers as plain string emitters (no JavaPoet). Property mapping: `text`/`promptText` → `setText`-style builders exactly as golden files define; `prefWidth`/`prefHeight`/`maxWidth` → `setPrefWidth(...)` etc. (double literals); `spacing` → constructor or setter per golden; `alignment` → `setAlignment(Pos.valueOf("..."))`.
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `git add jmifx-gradle/codegen && git commit -m "feat: java view-class and index emitters (golden-file tested)"`

---

### Task 6: jmifx-codegen — compile-and-instantiate integration test

**Files:**
- Test: `jmifx-gradle/codegen/src/test/java/com/jmifx/codegen/CodegenCompileIT.java`

**Interfaces:**
- Consumes: `FxmlViewCompiler` output (generated sources), `jmifx` framework classes (added as test dependency `testImplementation project(':jmifx')` — NOT possible: codegen is in the composite build. Instead: compile generated sources against the **root build's jmifx jar** published to a local dir — simplest: the test compiles against `org.openjfx:javafx-controls` + a **minimal hand-written stub** of `com.jmifx.FxView` (`interface FxView { String getId(); Parent getRoot(); }`) placed in codegen test sources. The real framework link is proven in Task 7's functional test.)

- [ ] **Step 1: Write the failing test** — run compiler on `valid-hello.fxml` → compile `HelloView.java` + `FxViewsIndex.java` with `ToolProvider.getSystemJavaCompiler()` (classpath: test-classes dir with `fixture.HelloController` + `FxView` stub + `javafx-controls`) into a temp dir → load by URLClassLoader → `new HelloView()` → assert: `getId()` equals `"hello-view"`; root is `VBox` with exactly 3 children `Label, TextField, Button` in order; `greetButton.getText()` equals `"Greet"`; `greetButton.getOnAction()` (or `onActionProperty`) is non-null; controller fields were injected (test controller records injections in a `public List<String> injected` populated from `initialize()`).
- [ ] **Step 2: Run — expect FAIL** (or compile errors in generated code — fix emission, not the test).
- [ ] **Step 3: Make it pass** — adjust emitters if the generated code fails javac; keep golden files in sync (both must pass).
- [ ] **Step 4: Run — expect PASS.** Run: `./gradlew -p jmifx-gradle :codegen:test`
- [ ] **Step 5: Commit** — `git add jmifx-gradle/codegen && git commit -m "test: generated views compile and wire controllers correctly"`

---

### Task 7: jmifx-gradle plugin — `generateFxViews` task

**Files:**
- Create: `jmifx-gradle/plugin/build.gradle` (`java-gradle-plugin`, `gradlePlugin { plugins { jmifx { id = 'jmifx'; implementationClass = 'com.jmifx.gradle.JmifxPlugin' } } }`, `implementation project(':codegen')`, TestKit deps)
- Create: `jmifx-gradle/plugin/src/main/java/com/jmifx/gradle/JmifxPlugin.java`, `JmifxPluginExtension.java`, `GenerateFxViewsTask.java`
- Test: `jmifx-gradle/plugin/src/test/java/com/jmifx/gradle/GenerateFxViewsFunctionalTest.java` + fixture project `jmifx-gradle/plugin/src/test/fixtures/hello-project/`

**Interfaces:**
- Consumes: `FxmlViewCompiler` (Task 4/5).
- Produces (consumed by Task 8 and demo client modules): Gradle plugin id `jmifx`, applied to client modules. Extension `jmifx { fxmlSourceDir = 'src/main/fxml' (default); generatedDir = 'build/generated/fxviews' (default); mainClass (String, required for Task 8) }`. Behavior: applies `java`; registers `generateFxViews` (inputs: `src/main/fxml/**/*.fxml`; outputs: generated dir; runs compiler in-process; on errors fails the task printing every `FxmlCompileError.format()` line); adds generated dir to `sourceSets.main.java`; `compileJava.dependsOn(generateFxViews)`.

- [ ] **Step 1: Write the failing functional test** — TestKit with plugin-under-test metadata (standard Gradle docs pattern). Fixture project: `plugins { id 'jmifx' }` + `repositories` block matching global constraints + `dependencies { compileOnly 'org.openjfx:javafx-controls:25' }` + `src/main/fxml/hello-view.fxml` (fx:controller `client.HelloController`) + `src/main/java/client/HelloController.java` (package-visible `@FXML` fields + `initialize()`). Test runs `generateFxViews` → asserts `build/generated/fxviews/client/HelloView.java` exists; then runs `compileJava` → BUILD SUCCESSFUL. Error case: second fixture with an unsupported element → run fails and output contains `<file>:<line>: unsupported element 'TableView'`.
- [ ] **Step 2: Run — expect FAIL** (plugin id unresolvable).
- [ ] **Step 3: Implement plugin + extension + task.**
- [ ] **Step 4: Run — expect PASS.** Run: `./gradlew -p jmifx-gradle :plugin:test`
- [ ] **Step 5: Commit** — `git add jmifx-gradle/plugin && git commit -m "feat: jmifx gradle plugin with generateFxViews task"`

---

### Task 8: jmifx-gradle plugin — TeaVM wasmGC wiring and web packaging

**Files:**
- Modify: `jmifx-gradle/plugin/build.gradle` (add `implementation 'org.teavm:teavm-gradle-plugin:0.14.1'` — coordinates verified on Maven Central; adjust if the spike recorded a different working version)
- Create: `jmifx-gradle/plugin/src/main/java/com/jmifx/gradle/TeaVmWiring.java`, `PackageJmifxWebTask.java`; resource `jmifx-gradle/plugin/src/main/resources/jmifx/index-template.html`
- Test: extend the functional test with `TeaVmWiringFunctionalTest.java`

**Interfaces:**
- Consumes: spike findings document (exact artifact list + DSL that worked).
- Produces: plugin additionally — applies `org.teavm`; configures `teavm { all { mainClass = jmifx.mainClass } wasmGC { …spike-validated DSL… } }`; adds the spike's WebFX Kit dependency list to the configuration the TeaVM compile consumes (mechanism per spike: kit provides `javafx.*`; `org.openjfx` must stay off that classpath); registers `packageJmifxWeb` copying the teavm wasm output + `classes.wasm-runtime.js` + rendered `index.html` (template tokens `{{WASM_FILE}}`, `{{RUNTIME_JS}}` replaced from actual output names) into `build/jmifx-web/jmifx-web/`; registers `build/jmifx-web` as an additional `sourceSets.main.resources.srcDir` (**never** writes into `build/resources/main`); `assemble.dependsOn(packageJmifxWeb)`; `packageJmifxWeb.dependsOn(teavm wasm compile task)`.

- [ ] **Step 1: Write the failing functional test** — fixture project (Task 7's plus `jmifx { mainClass = 'client.DemoFxApp' }` + a trivial `client.DemoFxApp extends com.jmifx.FxApplication`). Test runs `jmifxHelp`-level checks: `tasks.named('packageJmifxWeb')` exists; the teavm extension's `mainClass` is `client.DemoFxApp`; dry-run `assemble --dry-run` lists both teavm compile and `packageJmifxWeb`. Full wasm compile is **not** run here (network/minutes — proven in Task 10/11); the test asserts wiring only.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement wiring + packaging task + template.**
- [ ] **Step 4: Run — expect PASS.** Run: `./gradlew -p jmifx-gradle :plugin:test`
- [ ] **Step 5: Commit** — `git add jmifx-gradle/plugin && git commit -m "feat: teavm wasmGC wiring and jmifx-web packaging in gradle plugin"`

---

### Task 9: jmifx-starter — serve `/fx/**`

**Files:**
- Create: `jmifx-starter/build.gradle` (deps: `compileOnly`/`implementation` `org.springframework.boot:spring-boot-autoconfigure` via `platform('org.springframework.boot:spring-boot-dependencies:4.1.1')`; tests with `spring-boot-starter-test`)
- Create: `jmifx-starter/src/main/java/com/jmifx/starter/JmifxProperties.java` (`jmifx.enabled` default true, `jmifx.path` default `/fx`), `JmifxAutoConfiguration.java`
- Create: `jmifx-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `jmifx-starter/src/test/java/com/jmifx/starter/JmifxAutoConfigurationTest.java` + fixture asset `src/test/resources/jmifx-web/index.html`

**Interfaces:**
- Produces: auto-configuration that registers a resource handler mapping `${jmifx.path}/**` → `classpath:/jmifx-web/` and a welcome forwarding (`GET ${jmifx.path}/` → `${jmifx.path}/index.html`); inactive (no mappings) when `jmifx.enabled=false`. Missing assets must not prevent context startup (resource handlers are lazy).

- [ ] **Step 1: Write failing tests** (MockMvc via `ApplicationContextRunner` + `WebMvcAutoConfiguration`-style slice, or a minimal `@SpringBootConfiguration` + `@AutoConfiguration` import — pick the lightest that works on Boot 4.1):
  - `servesIndexHtml` — GET `/fx/index.html` → 200, body equals fixture content
  - `forwardsRootToIndex` — GET `/fx/` → 200 with same body
  - `serves404WhenAssetsMissing` — context without fixture asset on classpath → GET `/fx/index.html` → 404, context started
  - `disabledPropertyDisablesServing` — `jmifx.enabled=false` → GET `/fx/index.html` → 404
  - `customPathIsHonored` — `jmifx.path=/ui` → GET `/ui/index.html` → 200
- [ ] **Step 2: Run — expect FAIL.** Run: `./gradlew :jmifx-starter:test`
- [ ] **Step 3: Implement** properties + auto-config + imports file.
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `git add jmifx-starter && git commit -m "feat: jmifx-starter serves wasm client at /fx/**"`

---

### Task 10: jmifx-demo — Jmix app + client module

**Files:**
- Modify: `jmifx-demo/build.gradle` — plugins `id 'io.jmix' version '3.0.3'` + `apply plugin: 'org.springframework.boot'` (no version — Jmix classpath provides it, erp pattern); `apply plugin: 'java'`; dependencies: `project(':jmifx-starter')`, `jmix-starter-core`? — minimal set: `io.jmix.core:jmix-starter-core`, `runtimeOnly 'com.h2database:h2'`, `org.springframework.boot:spring-boot-starter-web`; `application.yml`: `spring.datasource.url: jdbc:h2:mem:demo`, username/password `sa`/``, `jmix.core.available-locales: en`
- Create: `jmifx-demo/src/main/java/com/jmifx/demo/DemoApplication.java` (`@SpringBootApplication` + main)
- Modify: `jmifx-demo-client/build.gradle` — `plugins { id 'jmifx' }`; `dependencies { implementation project(':jmifx'); compileOnly 'org.openjfx:javafx-controls:25' }`; `jmifx { mainClass = 'com.jmifx.demo.client.DemoFxApp' }`
- Create: `jmifx-demo-client/src/main/fxml/hello-view.fxml`, `src/main/java/com/jmifx/demo/client/HelloViewController.java`, `DemoFxApp.java`

**Interfaces:**
- Consumes: plugin (Tasks 7–8), starter (Task 9), framework (Task 3).
- Produces: the acceptance deliverable. `hello-view.fxml`: VBox(spacing=10, alignment=CENTER) → `Label fx:id="greetingLabel" text="Hello!"`, `TextField fx:id="nameField" promptText="Type your name"`, `Button fx:id="greetButton" text="Greet" onAction="#greet"`. Controller: package-visible `@FXML` fields, `public void initialize()`, `greet(javafx.event.ActionEvent e)` sets `greetingLabel.setText(nameField.getText().isBlank() ? "Hello!" : "Hello, " + nameField.getText() + "!")`. `DemoFxApp extends FxApplication`: `getStartupViewId()` = `"hello-view"`, `registerViews()` calls `FxViewsIndex.registerAll(registry)`.

- [ ] **Step 1: Write the four client files and the two app files.**
- [ ] **Step 2: Build the client to Wasm** — `./gradlew :jmifx-demo-client:assemble` → assert `jmifx-demo-client/build/jmifx-web/jmifx-web/` contains `index.html`, the `.wasm` file, and the runtime `.js`.
- [ ] **Step 3: Build the app jar** — `./gradlew :jmifx-demo:bootJar` → assert jar contains `BOOT-INF/classes/jmifx-web/index.html` (unzip -l).
- [ ] **Step 4: Sanity-run tests** — `./gradlew build` green overall.
- [ ] **Step 5: Commit** — `git add jmifx-demo jmifx-demo-client && git commit -m "feat: jmifx-demo jmix 3.0.3 app with wasm hello view"`

---

### Task 11: E2E acceptance — browser verification

**Files:**
- Create: `docs/superpowers/e2e/m1-acceptance.md` (results + screenshots)
- Modify: `docs/superpowers/brainstorming-checklist.md` (final status)

**Interfaces:**
- Consumes: running demo app (Task 10).

- [ ] **Step 1: Start the app** — `./gradlew :jmifx-demo:bootRun` in background; wait for port 8080.
- [ ] **Step 2: Verify with agent-browser** — open `http://localhost:8080/fx/`; assert: the three components render (spec §9); type `Eduardo` into the field; click Greet; assert label reads `Hello, Eduardo!`; screenshot → `docs/superpowers/e2e/m1-browser.png`. Also check the browser console for errors (record any).
- [ ] **Step 3: If the flow fails** — debug via systematic-debugging skill; fixes go through the owning task's tests, not ad-hoc patches.
- [ ] **Step 4: Stop the app; write `m1-acceptance.md`** — pass/fail per acceptance criterion, environment (browser, versions), known issues.
- [ ] **Step 5: Commit** — `git add docs/superpowers && git commit -m "docs: milestone 1 e2e acceptance results"`

---

## Self-Review (performed after writing — results)

- Spec coverage: §4 structure→Task 1 (composite deviation noted), §5 framework→Task 3, §5 subset/§6 validation→Tasks 4–6, §7 build→Tasks 2/7/8, §8 starter→Task 9, §9 demo→Tasks 10–11, §10 testing→each task, §11 risks→spike + pinned versions. No gaps.
- Step scan: no TBDs; code blocks carry only signatures/templates/pinned values.
- Type consistency: `FxmlViewCompiler.compile(List<Path>, Path)` used identically in Tasks 4/5/7; `FxViewsIndex.registerAll(FxViewRegistry)` consistent in Tasks 3/5/10; view id `"hello-view"` consistent throughout.
- Review Focus: five items, each with its test pinned to Tasks 4 and 9.
- Proportion: plan ≈ 1.5× spec; emitters described by golden files rather than transcribed bodies.
