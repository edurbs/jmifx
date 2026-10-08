# Milestone 2 — City Detail View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the first data round trip — a city detail view whose Save button persists a `City` row via the Jmix generic REST API — plus two reusable client-framework blocks (`FxHttp`, `FxNavigation`).

**Architecture:** The wasm client POSTs hand-built JSON to `POST /rest/entities/City` (jmix-rest-starter, anonymous access via API-scope resource role) served by the same Jmix app (same-origin, no CORS). Persistence happens inside the Jmix REST machinery (no application persistence code); the client gets a dual-platform `FxHttp` (transport decided by the opening spike) and a static `FxNavigation` facade bound by `FxApplication`.

**Tech Stack:** Jmix 3.0.3 (BOM), Spring Boot 4.1, H2 (file), Liquibase, TeaVM 0.14.1 + WebFX Kit 0.1.0-SNAPSHOT (wasmGC), Gradle 9.5.1, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-07-city-detail-m2-design.md` — the plan argues from the spec; executors read both.

**Plan-vs-spec refinement (flagged for plan review):** spec §4.1 said relative URLs resolve "against the page origin in the browser". Implementation instead resolves them against `System.getProperty("jmifx.base.url", "http://localhost:8080")` on every platform. Same-origin default means the demo is CORS-free and identical code runs on JVM and wasm — with zero platform-split classes. Page-origin detection (elemental2) is deferred with the desktop-JVM story. The spike (Task 1) must confirm `System.getProperty` returns the default (not throw) under TeaVM.

## Global Constraints

- Client/wasm code path (`jmifx`, `jmifx-demo-client` main sources): **zero reflection, zero Spring/Jmix imports.**
- WebFX Kit (`dev.webfx:*`): **`compileOnly` + `teavmClasspath` only — never `implementation`** (kit on the server classpath crashes hibernate-validator at boot).
- `org.openjfx` only on JVM test classpaths, with explicit `linux` classifiers; client main compiles against kit emul jars.
- TeaVM Gradle plugin pinned **0.14.1**; module bytecode **`--release 21`**; Gradle 9.5.1; JDK 25 runs Gradle.
- Any test touching JavaFX controls runs under **`xvfb-run -a`**.
- Repositories declared only in `settings.gradle` (`FAIL_ON_PROJECT_REPOS`).
- Spring Boot 4.1 artifact names: `spring-boot-starter-webmvc`, `spring-boot-webmvc-test` (no `spring-boot-starter-web`).
- Jmix version via `jmix { bomVersion = '3.0.3' }`; Jmix coordinates as in this plan (`io.jmix.data:jmix-eclipselink-starter`, `io.jmix.rest:jmix-rest-starter`).
- Packaged web assets only via `build/jmifx-web` + `jar.from(...)`; never into `build/resources/main`.
- Conventional commits: `feat:`, `fix:`, `test:`, `docs:`, `build:`, `spike:`.
- TDD: failing test first. Design rulings discovered during execution go to `docs/superpowers/plans/ledger-m2.md` (create it in Task 1), not commit messages.
- FXML subset is unchanged this milestone — no codegen/golden changes belong in any task.

## Review Focus

Inputs/failure modes the spec implies but task tests might miss — each pinned by the listed test:

1. **JSON-hostile city names** (quotes, backslash, newlines, `\u0000`–`\u001F`) must round-trip intact → Task 2 `escapesQuotesBackslashNewlineAndControlChars`; E2E (Task 8) saves `São "Paulo"`.
2. **Non-2xx responses must not read as success** → Task 3 `serverErrorSurfacesStatusCode` (asserts `onResult(500, …)`, no `onFailure`).
3. **Server unreachable mid-save** → Task 3 `unreachableServerCallsOnFailure` (connection refused → `onFailure`, listener still on FX thread).
4. **Listener thread-safety** (transport completes off the FX thread) → Task 3 `listenerRunsOnFxApplicationThread`.
5. **Security accidentally wide open** (anonymous pattern too broad, or none needed) → Task 6 `endpointOutsideAnonymousPatternIsRejected` (`/rest/userInfo` without token → 4xx, never 2xx).

---

### Task 1: Spike — HTTP round trip from wasm

**Files:**
- Modify: `spike-webfx/src/main/java/com/jmifx/spike/SpikeApp.java`
- Create: `spike-webfx/echo-server.py`
- Modify: `docs/superpowers/spikes/2026-10-06-webfx-teavm-gradle.md` (append HTTP section)
- Create: `docs/superpowers/plans/ledger-m2.md`

**Interfaces:**
- Consumes: existing spike app (Label + TextField + Button pattern), `WebFxKitLauncher` launch idiom.
- Produces: spike-doc section "HTTP from wasm" — the **transport verdict** (`A` = `java.net.HttpURLConnection` emulated by TeaVM, or `B` = elemental2 `fetch`), plus: does `System.getProperty("jmifx.spike", "default")` return `"default"` without throwing under TeaVM; exact elemental2 artifact coordinates resolvable from the kit classpath; whether the UI freezes during the request; whether `ServiceLoader.load(Supplier.class)` finds a provider under TeaVM. Tasks 3 and 7 read this section as their authority.

- [ ] **Step 1: Add an HTTP probe to the spike app**

Add a second Button ("POST probe") whose handler:
1. shows `System.getProperty("jmifx.spike", "default")` in the label (probe A: property default under TeaVM);
2. does **route A first**: `new URL("http://localhost:8090/echo").openConnection()` as `HttpURLConnection`, `POST`, `Content-Type: application/json`, write `{"ping":"pong"}`, read status + response body into the label; any `Throwable` → label shows the error (do not crash).

- [ ] **Step 2: Write `spike-webfx/echo-server.py`**

Single-port server (same-origin, so no CORS): serves files from `build/generated/teavm/wasm-gc/` and answers `POST /echo` by returning `{"status":"ok","echo":<request body>}` with `200`; anything else 404. Python 3 stdlib only (`http.server`). Port 8090.

- [ ] **Step 3: Build the wasm**

Run: `./gradlew -p spike-webfx buildWasmGC`
Expected: BUILD SUCCESSFUL. If TeaVM rejects `java.net.HttpURLConnection` (missing class/method), record the exact error — route A is dead; go to Step 6.

- [ ] **Step 4: Verify in a real browser**

Run `python3 spike-webfx/echo-server.py` (background), then agent-browser (skill) to `http://localhost:8090/`, click the POST probe. Expected: label shows the property probe result AND `200` + echo body. Note: does the UI freeze? Console errors?

- [ ] **Step 5: Record findings and commit (route A works)**

Append `## HTTP from wasm (2026-10-07)` to the spike doc: verdict **A**, the working code idiom, `System.getProperty` behavior, UI-freeze observation. Create `docs/superpowers/plans/ledger-m2.md` with header mirroring `ledger-m1.md`. Commit: `spike: verify HTTP from wasm (route A)`.

- [ ] **Step 6 (only if route A failed): elemental2 fetch probe, then commit**

Find elemental2 coordinates: `./gradlew -p spike-webfx dependencies --configuration compileClasspath | grep -i elemental2`. Replace the handler with `DomGlobal.fetch("/echo", request-init-with-method-POST-and-body)` + promise callbacks into the label; rebuild, re-verify. Also probe `ServiceLoader` (one tiny interface + impl + `META-INF/services` entry — does TeaVM find it?). Record verdict **B** (+ ServiceLoader result) in the spike doc; commit `spike: verify HTTP from wasm (route B)`.

### Task 2: `FxJson` — minimal JSON writer

**Files:**
- Create: `jmifx/src/main/java/com/jmifx/FxJson.java`
- Test: `jmifx/src/test/java/com/jmifx/FxJsonTest.java`

**Interfaces:**
- Produces: `public final class FxJson` with `public static String obj(String key, String value)` → `{"key":"value"}` (`null` value → `{"key":null}`). Used by Task 7's controller.

- [ ] **Step 1: Write the failing tests**

`FxJsonTest` (plain JUnit, no FX): `plainValue` → `{"name":"Berlin"}`; `escapesQuotesBackslashNewlineAndControlChars` — input `São "Paulo"\` + `\n` + `\u0000` + `\u001F` → output contains `\"`, `\\`, `\n`, `\u0000`, `\u001f` and round-trips as one JSON string; `nullValueBecomesNullLiteral` → `{"name":null}`.

- [ ] **Step 2: Run to verify failure**

Run: `xvfb-run -a ./gradlew :jmifx:test --tests 'FxJsonTest'`
Expected: compile FAILURE (`FxJson` does not exist).

- [ ] **Step 3: Implement `FxJson`**

Static `obj`; private `escape(String)` escaping `"` `\` and `\u0000`–`\u001F` (shortcuts `\n` `\r` `\t`, else `\u00xx` lowercase hex). No reflection, no dependencies.

- [ ] **Step 4: Run to verify pass** — same command, PASS.

- [ ] **Step 5: Commit** — `git add jmifx/src/main/java/com/jmifx/FxJson.java jmifx/src/test/java/com/jmifx/FxJsonTest.java && git commit -m "feat: FxJson minimal JSON writer"`

### Task 3: `FxHttp` — dual-platform async POST

**Files (Variant A — expected):**
- Create: `jmifx/src/main/java/com/jmifx/FxHttp.java`
- Test: `jmifx/src/test/java/com/jmifx/FxHttpTest.java`
- Modify: `jmifx/build.gradle` (only if a compileOnly dep is needed for the verdict-A idiom)

**Files (Variant B — only if spike says A is dead; requires a plan-level escalation note in the ledger before proceeding):**
- As above, plus Create modules `jmifx-http-wasm/` (elemental2 `fetch` transport + `META-INF/services`) and `jmifx-http-jvm/` (`java.net.http` transport + `META-INF/services`), Modify root `settings.gradle`, and the plugin's teavmClasspath wiring in `jmifx-gradle/plugin`. The shared `jmifx` module then contains only the facade + `ServiceLoader.load(FxHttpTransport.class)` selection.

**Interfaces:**
- Consumes: spike-doc "HTTP from wasm" verdict (Task 1).
- Produces (both variants): `public final class FxHttp` with
  - `public interface Listener { void onResult(int statusCode, String body); void onFailure(Throwable t); }`
  - `public static void post(String url, String jsonBody, Listener listener)`
  - contract: URL resolved by `resolveUrl`; `onResult` for any HTTP response (2xx/4xx/5xx), `onFailure` for network-level failure; listener callbacks **always marshaled via `Platform.runLater`**; re-entrancy allowed.
  - `static String resolveUrl(String url)` (package-private, test-visible): absolute `http(s)://` URLs pass through; otherwise prefixed with `System.getProperty("jmifx.base.url", "http://localhost:8080")` (single `/` join); `System.getProperty` guarded by try/catch → default (TeaVM may not implement properties).

- [ ] **Step 1: Write the failing tests**

`FxHttpTest` (`@BeforeAll FxTestKit.start()`; local `com.sun.net.httpserver.HttpServer` on an ephemeral port; `CountDownLatch` to await async results):
- `postRoundTripsJsonBody` — echo handler → `onResult(200, body)`; body received equals sent JSON.
- `serverErrorSurfacesStatusCode` — handler returns 500 → `onResult(500, …)`, no `onFailure`.
- `unreachableServerCallsOnFailure` — server stopped/closed port → `onFailure` with connect exception.
- `listenerRunsOnFxApplicationThread` — inside both callbacks assert `Platform.isFxApplicationThread()`.
- `resolveUrl` cases: absolute passthrough; `/rest/entities/City` + default → `http://localhost:8080/rest/entities/City`; with `jmifx.base.url` set (and unset in `@AfterEach`) → custom base, exactly one `/` at the join.

- [ ] **Step 2: Run to verify failure**

Run: `xvfb-run -a ./gradlew :jmifx:test --tests 'FxHttpTest'`
Expected: compile FAILURE (`FxHttp` does not exist).

- [ ] **Step 3: Implement `FxHttp` per the spike verdict**

Variant A: one transport using `java.net.HttpURLConnection` (API-identical on JVM and TeaVM emul; TeaVM makes it synchronous on the caller thread — acceptable, UI freeze recorded by spike). JVM behavior may be sync or async; either satisfies the contract. Read status + body (error stream on ≥400), wrap in try/catch → `onFailure`. Every callback goes through `Platform.runLater`. Variant B: facade + `ServiceLoader` per the Files block; tests add `testImplementation project(':jmifx-http-jvm')`.

- [ ] **Step 4: Run to verify pass** — same command, PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: FxHttp dual-platform async POST"` (with the exact files of the chosen variant; Variant B adds a ledger ruling first).

### Task 4: `FxNavigation` — controller-usable navigation

**Files:**
- Create: `jmifx/src/main/java/com/jmifx/FxNavigation.java`
- Modify: `jmifx/src/main/java/com/jmifx/FxApplication.java` (bind after navigator construction, before startup navigate)
- Test: `jmifx/src/test/java/com/jmifx/FxNavigationTest.java`

**Interfaces:**
- Consumes: `FxNavigator.navigateTo(String)`, `FxViewRegistry.register(String, Supplier<FxView>)` (existing).
- Produces: `public final class FxNavigation` with `public static void navigateTo(String viewId)` delegating to the bound navigator; package-private `static void bind(FxNavigator navigator)` (volatile field) called once from `FxApplication.start`. Unbound call → `IllegalStateException("FxNavigation not bound — FxApplication.start must run first")`. Used by Task 7 controllers.

- [ ] **Step 1: Write the failing tests**

`FxNavigationTest` (`FxTestKit.start()`; tests must reset binding via `FxNavigation.bind(null)` in `@AfterEach` since it is static):
- `boundNavigatorReceivesNavigation` — bind to a navigator over a registered registry + pane; `FxNavigation.navigateTo("v")` installs the view root.
- `unboundNavigationThrowsIllegalState` — bind(null); assert `IllegalStateException` with message containing `not bound`.
- `applicationStartBindsNavigator` — minimal `FxApplication` subclass (startup view `v`, registry with `v` and `other`); run `start(stage)` inside `Platform.runLater` + latch; then `FxNavigation.navigateTo("other")` must swap the content — `stage.getScene().getRoot()` is the `StackPane` content pane, and its single child becomes the `other` view root. Proves `start()` bound the facade.

- [ ] **Step 2: Run to verify failure** — `xvfb-run -a ./gradlew :jmifx:test --tests 'FxNavigationTest'` → compile FAILURE.

- [ ] **Step 3: Implement** — `FxNavigation` facade (volatile static field, delegate, `bind` idempotent-by-overwrite); `FxApplication.start`: after `new FxNavigator(...)`, call `FxNavigation.bind(navigator)` before `navigateTo(getStartupViewId())`.

- [ ] **Step 4: Run to verify pass** — same command, PASS; then full `xvfb-run -a ./gradlew :jmifx:test` (FxApplication/FxNavigator suites unaffected).

- [ ] **Step 5: Commit** — `git commit -m "feat: FxNavigation static facade bound by FxApplication"`

### Task 5: Demo server — `City` entity on H2 file store

**Files:**
- Modify: `jmifx-demo/build.gradle` (add `implementation 'io.jmix.data:jmix-eclipselink-starter'`; add `testImplementation 'org.springframework.boot:spring-boot-starter-test'`)
- Modify: `jmifx-demo/src/main/resources/application.yml` (replace the whole `spring.datasource` block with `main:` datasource/liquibase per below)
- Create: `jmifx-demo/src/main/java/com/jmifx/demo/entity/City.java`
- Create: `jmifx-demo/src/main/resources/com/jmifx/demo/liquibase/changelog.xml`
- Create: `jmifx-demo/src/main/resources/com/jmifx/demo/liquibase/010-create-city.xml`
- Test: `jmifx-demo/src/test/java/com/jmifx/demo/CityPersistenceTest.java`

**Interfaces:**
- Produces: `City` entity — `@JmixEntity @Entity @Table(name = "CITY")`, fields `UUID id` (`@JmixGeneratedValue @Id @Column(name="ID", nullable=false)`), `Integer version` (`@Version @Column(name="VERSION", nullable=false)`), `String name` (`@InstanceName @NotNull @Column(name="NAME", nullable=false)`) with getters/setters. Consumed by Task 6 (role references `City.class`) and Task 8 (H2 query against `CITY`).

- [ ] **Step 1: Write the failing test**

`CityPersistenceTest`: `@SpringBootTest` — autowire `DataManager`; create via `dataManager.create(City.class)`, set name `"Berlin"`, `dataManager.save(city)`; reload `dataManager.load(City.class).id(city.getId()).one()`; assert same name and `getVersion() == 1`. (Boots eclipselink + Liquibase → proves datasource, mapping, and changelog.)

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :jmifx-demo:test`
Expected: FAILURE (no entity/no datasource wiring — likely context boot error).

- [ ] **Step 3: Wire the store and entity**

`application.yml` (replace `spring.datasource` block; Jmix 3 convention):
```yaml
main:
  datasource:
    url: jdbc:h2:file:.jmix/h2/demo
    username: sa
    password: ""
  liquibase:
    change-log: com/jmifx/demo/liquibase/changelog.xml
```
Entity per the Interfaces block. `changelog.xml` includes `010-create-city.xml`; changeset `create-city`: `createTable CITY` — `ID UUID NOT NULL PK`, `VERSION INT NOT NULL`, `NAME VARCHAR(255) NOT NULL`. H2 stays `runtimeOnly` (already present). If liquibase-core is missing at boot, add `implementation 'org.liquibase:liquibase-core'` (ledger ruling).

- [ ] **Step 4: Run to verify pass** — `./gradlew :jmifx-demo:test` PASS (no FX involved — no xvfb needed).

- [ ] **Step 5: Commit** — `git commit -m "feat: City entity on H2 file store with liquibase"`

### Task 6: Demo server — anonymous generic REST for `City`

**Files:**
- Modify: `jmifx-demo/build.gradle` (add `implementation 'io.jmix.rest:jmix-rest-starter'` — pulls `jmix-security-resource-server` transitively; verified from its POM)
- Modify: `jmifx-demo/src/main/resources/application.yml` (add `jmix.resource-server.anonymous-url-patterns: /rest/entities/City/**`)
- Create: `jmifx-demo/src/main/java/com/jmifx/demo/security/CityRestRole.java`
- Test: `jmifx-demo/src/test/java/com/jmifx/demo/CityRestApiTest.java`

**Interfaces:**
- Consumes: `City` (Task 5).
- Produces: working anonymous `POST /rest/entities/City` (consumed by Task 7's client, pinned by Task 8 E2E); `CityRestRole` — `@ResourceRole(name="City REST", code="city-rest", scope="API")` with `@EntityPolicy(entityClass=City.class, actions={CREATE, READ})` + `@EntityAttributePolicy(entityClass=City.class, attributes="name", action=MODIFY)`.

**Known open point (resolve in Step 1, record ruling):** the documented way to grant the anonymous user a role is `DatabaseUserRepository.initAnonymousUser` (from `jmix-security-data`, needs the `User` entity). This demo has no users. Resolution order: (1) inspect what `jmix-security-resource-server` auto-configures for anonymous (classpath after Step "add dep" — look for an anonymous `UserDetails` provider/authority extension point); (2) docs: `https://docs.jmix.io/3.x/jmix/rest/access-control.html`. If neither yields a wiring materially lighter than security-data, **STOP and ask the human** — the spec's fallback (custom controller + `DataManager`) or adopting security-data is their call, not the executor's.

- [ ] **Step 1: Verify the anonymous wiring by hand first**

Add the dependency, role, and property; `./gradlew :jmifx-demo:bootRun`; `curl -i -X POST http://localhost:8080/rest/entities/City -H 'Content-Type: application/json' -d '{"name":"São \"Paulo\""}'`. Iterate (role assignment mechanism per the open point) until: this returns **201** and a `CITY` row exists. Record the mechanism as a ledger ruling.

- [ ] **Step 2: Write the failing tests**

`CityRestApiTest`: `@SpringBootTest` + MockMvc (`spring-boot-webmvc-test`):
- `anonymousCreateCityReturns201` — POST `/rest/entities/City` `{"name":"Springfield"}` (no auth header) → 201; then `DataManager` load by the returned `id` has that name. Also pins the REST entity name (`City`, not prefixed).
- `endpointOutsideAnonymousPatternIsRejected` — GET `/rest/userInfo` (no header) → 4xx (never 2xx).

- [ ] **Step 3: Run to verify failure** — `./gradlew :jmifx-demo:test` → new tests FAIL (or the context fails to boot without the Step-1 mechanism).

- [ ] **Step 4: Make them pass** — apply whatever Step 1 verified (role assignment bean/property); tests PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: anonymous generic REST create for City"`

### Task 7: Demo client — city detail view + navigation

**Files:**
- Create: `jmifx-demo-client/src/main/fxml/city-detail-view.fxml`
- Create: `jmifx-demo-client/src/main/java/com/jmifx/demo/client/CityDetailViewController.java`
- Modify: `jmifx-demo-client/src/main/fxml/hello-view.fxml` (add City button)
- Modify: `jmifx-demo-client/src/main/java/com/jmifx/demo/client/HelloViewController.java` (add handler)

**Interfaces:**
- Consumes: `FxHttp.post(String, String, FxHttp.Listener)`, `FxJson.obj`, `FxNavigation.navigateTo` (Tasks 2–4); codegen registers views as `city-detail-view` / `hello-view` via the regenerated `FxViewsIndex`.
- Produces: the E2E flow of Task 8.

- [ ] **Step 1: Author `city-detail-view.fxml`** (subset-only attributes)

VBox (`spacing="10"`, `alignment="CENTER"`, `prefWidth="400"`, `prefHeight="200"`, `fx:controller="com.jmifx.demo.client.CityDetailViewController"`) with: `Label fx:id="titleLabel" text="City detail"`; `TextField fx:id="cityField" promptText="Enter city name"`; HBox with `Button fx:id="saveButton" text="Save" onAction="#save"` and `Button fx:id="backButton" text="Back" onAction="#back"`; `Label fx:id="statusLabel" text=""`.

- [ ] **Step 2: Author the controllers**

`CityDetailViewController` (package-visible `@FXML`-style fields, mirroring `HelloViewController`): `save(ActionEvent)` — blank/whitespace name → `statusLabel "City name required"`, return; else `saveButton.setDisable(true)`, `FxHttp.post("/rest/entities/City", FxJson.obj("name", cityField.getText().trim()), listener)` where `onResult` 2xx → `"Saved: " + name`, otherwise → `"Save failed (HTTP " + statusCode + ")"`; `onFailure` → `"Save failed: " + t.getMessage()`; both re-enable the button. `back(ActionEvent)` → `FxNavigation.navigateTo("hello-view")`. `HelloViewController`: add `Button cityButton` field + `openCityDetail(ActionEvent)` → `FxNavigation.navigateTo("city-detail-view")`; FXML gains `<Button fx:id="cityButton" text="City" onAction="#openCityDetail"/>`.

- [ ] **Step 3: Build and verify codegen**

Run: `./gradlew :jmifx-demo-client:assemble`
Expected: BUILD SUCCESSFUL; `jmifx-demo-client/build/generated/fxviews/com/jmifx/demo/client/CityDetailView.java` exists; `FxViewsIndex.java` registers both view ids. Any failure here is a codegen subset violation with `file:line` — do not loosen codegen; fix the FXML.

- [ ] **Step 4: JVM smoke of the generated views (no test module — compile check only)**

Run: `./gradlew :jmifx-demo-client:compileJava`
Expected: SUCCESS (controllers compile against `jmifx` blocks; no Spring/Jmix imports introduced).

- [ ] **Step 5: Commit** — `git commit -m "feat: city detail view with save and navigation"`

### Task 8: E2E acceptance

**Files:**
- Create: `docs/superpowers/e2e/m2-acceptance.md`
- Create: `docs/superpowers/e2e/m2-browser.png` (and `m2-saved.png` if useful)

**Interfaces:**
- Consumes: everything above; H2 jar in the Gradle cache for the row check.

- [ ] **Step 1: Boot and drive the browser**

`./gradlew :jmifx-demo:bootRun` (background). agent-browser → `http://localhost:8080/fx/`: (1) hello view renders; (2) click **City** → city detail view renders; (3) click **Save** on empty field → `City name required`, no request; (4) type `São "Paulo"` → **Save** → label shows `Saved: São "Paulo"`; (5) console has no errors. Screenshots.

- [ ] **Step 2: Verify the row survived (file DB)**

Stop the app; `java -cp <h2-jar-from-gradle-cache> org.h2.tools.Shell -url 'jdbc:h2:file:.jmix/h2/demo' -user sa -password '' -sql "select name, version from CITY"` → exactly the row above with `version = 1`. Restart the app, re-run the query → row persists (file mode proven).

- [ ] **Step 3: Write `m2-acceptance.md`**

Mirror `m1-acceptance.md`: criteria table vs spec §9 flow, environment, fixes made during E2E, known limitations. Result: PASS only if every criterion holds.

- [ ] **Step 4: Full-suite verification**

Run: `xvfb-run -a ./gradlew build && ./gradlew -p jmifx-gradle test`
Expected: all green (root + composite; composite expected unchanged).

- [ ] **Step 5: Commit** — `git commit -m "docs: milestone 2 e2e acceptance results"`

---

## Self-review notes (already applied)

- Spec coverage: spec §2 REST/persistence/DB decisions → Tasks 5–6; §4 blocks → Tasks 2–4; §5 views → Task 7; §6 server wiring → Tasks 5–6; §7 error handling → Tasks 3+7 tests/E2E; §8 testing matrix → each task + Task 8; §9 risks → Task 1 spike + Task 6 open-point protocol; §10 deferred → out of plan. The URL-resolution refinement is flagged in the header for human review.
- Type consistency: `Listener.onResult(int, String)` / `onFailure(Throwable)`, `FxJson.obj(String, String)`, `FxNavigation.navigateTo(String)`, `City.getId()/getVersion()/getName()` used consistently across tasks.
- Proportion: code blocks limited to config values and test *names/assertions* the spec pins; bodies left to implementers.
