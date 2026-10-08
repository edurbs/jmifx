# AGENTS.md

jmifx compiles JavaFX/FXML views (Scene Builder) to WebAssembly via TeaVM + WebFX Kit, served by a Jmix 3.0.3 / Spring Boot 4.1 app at `/fx/**`.

**Goal:** build a Jmix add-on that renders JavaFX-authored views on both the JVM (desktop JavaFX) and the browser (WASM). Features are developed as reusable building blocks in the add-on (`jmifx` client framework, `jmifx-starter`, `jmifx-gradle` codegen/plugin); `jmifx-demo` exists only to exercise and prove those building blocks. Demo-only code that should really live in the add-on is a design smell.

Authoritative context (read before design changes):
- Spec: `docs/superpowers/specs/2026-10-06-jmifx-design.md`
- Spike findings (exact WebFX/TeaVM recipe): `docs/superpowers/spikes/2026-10-06-webfx-teavm-gradle.md`
- Execution rulings & known deferred issues: `docs/superpowers/plans/ledger-m1.md`

## Three separate Gradle builds (do not mix them up)

1. **Root build** (`./gradlew`): `jmifx` (client framework), `jmifx-starter` (Spring Boot auto-config for `/fx/**`), `jmifx-demo` (Jmix app), `jmifx-demo-client` (applies the `jmifx` plugin). Gradle 9.5.1, bytecode `--release 21`, JDK 25 runs Gradle.
2. **`jmifx-gradle/`** — separate composite build (own `settings.gradle`), included by the root via `pluginManagement { includeBuild(...) }`. Subprojects: `codegen` (FXML→Java compiler) and `plugin` (Gradle plugin id `jmifx`). Build/test it with `./gradlew -p jmifx-gradle ...`. `project()` dependencies cannot cross the composite boundary.
3. **`spike-webfx/`** — throwaway isolated spike build, intentionally NOT in root `settings.gradle`.

## Commands

```bash
./gradlew build                          # root build (see xvfb note below)
xvfb-run -a ./gradlew build              # use this: :jmifx tests need an X display
./gradlew -p jmifx-gradle test           # composite build: codegen + plugin (TestKit) tests
./gradlew :jmifx:test --tests 'FxNavigatorTest'   # single test class
./gradlew :jmifx-demo-client:assemble    # wasm build → build/jmifx-web/jmifx-web/
./gradlew :jmifx-demo:bootRun            # app at http://localhost:8080/fx/
```

- `:jmifx:test` and codegen's compile-IT **fail without a display** (JavaFX controls class-init; no Monocle in JavaFX 25 linux jars). Always wrap in `xvfb-run -a`.
- TeaVM's task is `buildWasmGC` (not wired into `assemble` by itself); raw output lands in `build/generated/teavm/wasm-gc/`.

## Hard constraints

- **Client/wasm code path: zero reflection, zero Spring/Jmix imports.**
- **WebFX Kit (`dev.webfx:*`) is `compileOnly` + `teavmClasspath` only — never `implementation`.** If it leaks onto the server classpath, hibernate-validator's JavaFX probes crash the app at boot (`NoClassDefFoundError: javafx/beans/property/MapProperty`).
- **Compile client code against WebFX Kit emul jars, not `org.openjfx`.** Gradle ignores openjfx's Maven profiles (resolves empty jars). `org.openjfx` is for JVM tests only, with explicit `linux` classifiers.
- **Launch via `FxApplication.launchApp(App::new, args)`** (→ `WebFxKitLauncher`). `Application.launch()` is stubbed in the kit and fails the TeaVM build.
- **TeaVM plugin pinned to 0.14.1** — must match `webfx-parent`'s `teavm.version`; do not bump independently.
- Packaged web assets go to `build/jmifx-web` and ride the client JAR via `jar.from(...)`; never write into `build/resources/main` (creates a task cycle: teavm compiles from `classes`).
- Repositories: `mavenCentral()` + WebFX snapshots (`https://central.sonatype.com/repository/maven-snapshots/`) + `https://global.repo.jmix.io/repository/public`. `FAIL_ON_PROJECT_REPOS` — declare repos in settings, not projects.
- Spring Boot 4.1 renamed artifacts: `spring-boot-starter-webmvc`, `spring-boot-webmvc-test` (no `spring-boot-starter-web`).

## FXML subset (enforced by codegen, errors are `file:line` build failures)

- Elements: `VBox, HBox, StackPane, Pane, Label, TextField, PasswordField, Button`. Root must be a container.
- Attributes: `fx:id, id, text, promptText, prefWidth, prefHeight, maxWidth, spacing, alignment, onAction` (`onAction` on Button only).
- `fx:controller` (FQCN) required; controllers need package-visible (min) `@FXML` fields and `void initialize()`.
- View id = FXML base name (`hello-view.fxml` → `hello-view`); generated class `HelloView` ("View" suffix not doubled). Generated classes live in the controller's package; a global `com.jmifx.generated.FxViewsIndex` registers all views.

## Conventions

- Conventional commits: `feat:`, `fix:`, `test:`, `docs:`, `build:`, `spike:`.
- TDD: failing test first; golden files in `jmifx-gradle/codegen/src/test/resources/golden/` pin generated-code shape — keep emitters and goldens in sync.
- Design decisions/rulings go in `docs/superpowers/` (spec → plan → ledger), not commit messages.
