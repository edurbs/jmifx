# Milestone 2 — Login + City Detail View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a real login flow and the first data round trip — a JavaFX login view (admin/admin via OAuth2 password grant) gating a city detail view whose Save button persists a `City` row through the Jmix generic REST API — plus reusable client-framework blocks (`FxHttp`+`FxAuth`, `FxJson`, `FxNavigation`) and a `PasswordField` codegen extension.

**Architecture:** The wasm client logs in by POSTing the password grant to `/oauth2/token` (Basic client auth), keeps the opaque `access_token` in `FxAuth`, and `FxHttp` auto-attaches `Authorization: Bearer …` on subsequent JSON POSTs to `/rest/entities/City` (jmix-rest-starter; admin's API-scope roles `rest-minimal` + `city-rest` authorize the save). Persistence happens inside the Jmix REST machinery; the client blocks are dual-platform (transport decided by the opening spike).

**Tech Stack:** Jmix 3.0.3 (BOM), Spring Boot 4.1, H2 (file), Liquibase, `jmix-authserver-starter`, `jmix-security-data-starter`, TeaVM 0.14.1 + WebFX Kit 0.1.0-SNAPSHOT (wasmGC), Gradle 9.5.1, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-07-city-detail-m2-design.md` (see its Change log — auth is login-based, **no anonymous access**). The plan argues from the spec; executors read both.

**Plan-vs-spec refinements (flagged for plan review):** (1) relative URLs resolve against `System.getProperty("jmifx.base.url", "http://localhost:8080")` on every platform rather than browser page-origin detection — same-origin default keeps the demo CORS-free with one code path; page-origin detection deferred.

## Global Constraints

- Client/wasm code path (`jmifx`, `jmifx-demo-client` main sources): **zero reflection, zero Spring/Jmix imports.**
- WebFX Kit (`dev.webfx:*`): **`compileOnly` + `teavmClasspath` only — never `implementation`** (kit on the server classpath crashes hibernate-validator at boot).
- `org.openjfx` only on JVM test classpaths, with explicit `linux` classifiers; client main compiles against kit emul jars.
- TeaVM Gradle plugin pinned **0.14.1**; module bytecode **`--release 21`**; Gradle 9.5.1; JDK 25 runs Gradle.
- Any test touching JavaFX controls runs under **`xvfb-run -a`** (root `:jmifx` tests and codegen's compile-IT).
- Repositories declared only in `settings.gradle` (`FAIL_ON_PROJECT_REPOS`).
- Spring Boot 4.1 artifact names: `spring-boot-starter-webmvc`, `spring-boot-webmvc-test`, `spring-boot-starter-test` (no `spring-boot-starter-web`).
- Jmix via `jmix { bomVersion = '3.0.3' }`.
- Packaged web assets only via `build/jmifx-web` + `jar.from(...)`; never into `build/resources/main`.
- The OAuth client secret ships inside the wasm for this demo (spec §2 accepted risk); do not spend effort hiding it.
- Conventional commits: `feat:`, `fix:`, `test:`, `docs:`, `build:`, `spike:`.
- TDD: failing test first. Execution rulings go to `docs/superpowers/plans/ledger-m2.md` (created in Task 1), not commit messages.

## Review Focus

Inputs/failure modes the spec implies but task tests might miss — each pinned by the listed test:

1. **JSON-hostile city names** (quotes, backslash, newlines, `\u0000`–`\u001F`) must round-trip intact → Task 2 `escapesQuotesBackslashNewlineAndControlChars`; E2E (Task 10) saves `São "Paulo"`.
2. **Non-2xx responses must not read as success** → Task 3 `serverErrorSurfacesStatusCode` (500 → `onResult`, no `onFailure`); Task 10 E2E wrong password shows "Login failed", stays on login view.
3. **Server unreachable mid-request** → Task 3 `unreachableServerCallsOnFailure`.
4. **Listener thread-safety** (transport completes off the FX thread) → Task 3 `listenerRunsOnFxApplicationThread`.
5. **Auth actually enforced** → Task 7 `createWithoutTokenIsUnauthorized` (401) and `wrongPasswordIsRejected` (4xx at the token endpoint); Task 3 `bearerAttachedOnlyWhenTokenSet`.

---

### Task 1: Spike — HTTP round trip from wasm

**Files:**
- Modify: `spike-webfx/src/main/java/com/jmifx/spike/SpikeApp.java`
- Create: `spike-webfx/echo-server.py`
- Modify: `docs/superpowers/spikes/2026-10-06-webfx-teavm-gradle.md` (append HTTP section)
- Create: `docs/superpowers/plans/ledger-m2.md`

**Interfaces:**
- Consumes: existing spike app (Label + TextField + Button pattern), `WebFxKitLauncher` launch idiom.
- Produces: spike-doc section "HTTP from wasm" — the **transport verdict** (`A` = `java.net.HttpURLConnection` emulated by TeaVM, or `B` = elemental2 `fetch`), plus: does `System.getProperty("jmifx.spike", "default")` return `"default"` without throwing; does `java.util.Base64.getEncoder().encodeToString("probe".getBytes(UTF_8))` work; whether the UI freezes during the request; whether `ServiceLoader.load(Supplier.class)` finds a provider (only probed if route A dies). Tasks 3, 8, 9 read this section as their authority.

- [ ] **Step 1: Add an HTTP probe to the spike app**

Add a second Button ("POST probe") whose handler: (1) shows `System.getProperty("jmifx.spike", "default")` in the label; (2) shows `java.util.Base64.getEncoder().encodeToString("probe".getBytes(StandardCharsets.UTF_8))`; (3) does **route A first**: `new URL("http://localhost:8090/echo").openConnection()` as `HttpURLConnection`, `POST`, `Content-Type: application/json`, write `{"ping":"pong"}`, read status + response body into the label; any `Throwable` → label shows the error.

- [ ] **Step 2: Write `spike-webfx/echo-server.py`**

Single-port server (same-origin, so no CORS): serves files from `build/generated/teavm/wasm-gc/` and answers `POST /echo` by returning `{"status":"ok","echo":<request body>}` with `200`; anything else 404. Python 3 stdlib only (`http.server`). Port 8090.

- [ ] **Step 3: Build the wasm**

Run: `./gradlew -p spike-webfx buildWasmGC`
Expected: BUILD SUCCESSFUL. If TeaVM rejects `java.net.HttpURLConnection` (missing class/method), record the exact error — route A is dead; go to Step 6.

- [ ] **Step 4: Verify in a real browser**

Run `python3 spike-webfx/echo-server.py` (background), then agent-browser (skill) to `http://localhost:8090/`, click the POST probe. Expected: label shows the property probe, the Base64 probe, AND `200` + echo body. Note: does the UI freeze? Console errors?

- [ ] **Step 5: Record findings and commit (route A works)**

Append `## HTTP from wasm (2026-10-07)` to the spike doc: verdict **A**, the working code idiom, `System.getProperty` + Base64 behavior, UI-freeze observation. Create `docs/superpowers/plans/ledger-m2.md` with a header mirroring `ledger-m1.md`. Commit: `spike: verify HTTP from wasm (route A)`.

- [ ] **Step 6 (only if route A failed): elemental2 fetch probe, then commit**

Find elemental2 coordinates: `./gradlew -p spike-webfx dependencies --configuration compileClasspath | grep -i elemental2`. Replace the handler with `DomGlobal.fetch("/echo", …POST + body…)` + promise callbacks into the label; rebuild, re-verify. Also probe `ServiceLoader` (tiny interface + impl + `META-INF/services` entry). Record verdict **B** in the spike doc; commit `spike: verify HTTP from wasm (route B)`.

### Task 2: `FxJson` — minimal JSON write + single-value read

**Files:**
- Create: `jmifx/src/main/java/com/jmifx/FxJson.java`
- Test: `jmifx/src/test/java/com/jmifx/FxJsonTest.java`

**Interfaces:**
- Produces: `public final class FxJson` with
  - `public static String obj(String key, String value)` → `{"key":"value"}` (`null` value → `{"key":null}`)
  - `public static String stringValue(String json, String key)` → unescaped value of a **top-level** string member, or `null` if absent
  - Used by Tasks 8 (token extraction) and 9 (save body).

- [ ] **Step 1: Write the failing tests**

`FxJsonTest` (plain JUnit, no FX): `plainValue` → `{"name":"Berlin"}`; `escapesQuotesBackslashNewlineAndControlChars`; `nullValueBecomesNullLiteral`; `stringValueExtractsTopLevelMember` — token-response-shaped JSON → extracts `access_token`; `stringValueReturnsNullForMissingKey`; `stringValueUnescapesValue` (`\"` → `"`).

- [ ] **Step 2: Run to verify failure**

Run: `xvfb-run -a ./gradlew :jmifx:test --tests 'FxJsonTest'` → compile FAILURE (`FxJson` missing).

- [ ] **Step 3: Implement `FxJson`**

`escape(String)` for writing (`"` `\` and `\u0000`–`\u001F`, shortcuts `\n` `\r` `\t`, else `\u00xx` lowercase); `stringValue` scans for `"key"` at depth 0 then reads the following string literal and unescapes `\"` `\\` `\n` `\t` `\uXXXX`. No reflection, no deps.

- [ ] **Step 4: Run to verify pass** — same command, PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: FxJson minimal JSON write and single-value read"`

### Task 3: `FxHttp` + `FxAuth` — dual-platform POST with OAuth support

**Files (Variant A — expected):**
- Create: `jmifx/src/main/java/com/jmifx/FxHttp.java`
- Create: `jmifx/src/main/java/com/jmifx/FxAuth.java`
- Test: `jmifx/src/test/java/com/jmifx/FxHttpTest.java`
- Modify: `jmifx/build.gradle` (only if a compileOnly dep is needed for the verdict-A idiom)

**Files (Variant B — only if the spike verdict is B; add a ledger ruling first):** as above plus modules `jmifx-http-wasm/` (elemental2 transport + `META-INF/services`) and `jmifx-http-jvm/` (`java.net.http` transport + `META-INF/services`), Modify root `settings.gradle` and the plugin's teavmClasspath wiring in `jmifx-gradle/plugin`; the shared `jmifx` module keeps only the facade + `ServiceLoader.load(FxHttpTransport.class)` selection.

**Interfaces:**
- Consumes: spike-doc "HTTP from wasm" verdict (Task 1).
- Produces (both variants):
  - `public final class FxHttp` with
    - `public interface Listener { void onResult(int statusCode, String body); void onFailure(Throwable t); }`
    - `public static void post(String url, String jsonBody, Listener listener)` — JSON; **auto-attaches `Authorization: Bearer <FxAuth token>` when set**; used by Task 9.
    - `public static void postForm(String url, String formBody, String basicAuth, Listener listener)` — `application/x-www-form-urlencoded`; sets `Authorization` to `basicAuth` when non-null; **no auto-Bearer**; used by Task 8 for the token endpoint.
    - `public static String basic(String username, String password)` — `"Basic " + Base64(user:pass)`.
    - `public static String urlEncode(String s)` — UTF-8 percent-encoding, space → `%20`.
    - `static String resolveUrl(String url)` (package-private): absolute passthrough; else prefix `System.getProperty("jmifx.base.url", "http://localhost:8080")` (guarded by try/catch → default), single `/` join.
    - contract: `onResult` for any HTTP response, `onFailure` for network-level failure; callbacks always via `Platform.runLater`.
  - `public final class FxAuth` with `static String getAccessToken()`, `static void setAccessToken(String)`, `static void clear()`, `static boolean isLoggedIn()` (volatile, memory-only).

- [ ] **Step 1: Write the failing tests**

`FxHttpTest` (`@BeforeAll FxTestKit.start()`; local `com.sun.net.httpserver.HttpServer` capturing method/headers/body; `CountDownLatch`; `FxAuth.clear()` in `@AfterEach`):
- `postRoundTripsJsonBody` — 200, body received equals sent JSON, content type `application/json`.
- `serverErrorSurfacesStatusCode` — 500 → `onResult(500, …)`, no `onFailure`.
- `unreachableServerCallsOnFailure` — closed port → `onFailure`.
- `listenerRunsOnFxApplicationThread` — assert `Platform.isFxApplicationThread()` inside both callbacks.
- `postFormSendsBasicAuthAndFormBody` — server observes `Authorization: Basic …` (equals `FxHttp.basic("u","p")`) and the exact form body.
- `bearerAttachedOnlyWhenTokenSet` — with `FxAuth.setAccessToken("tok")` server observes `Authorization: Bearer tok`; after `clear()`, header absent.
- `resolveUrl` cases (absolute passthrough; default base; `jmifx.base.url` override, set/unset around the test).
- `urlEncodePinsKnownValues` — `"São Paulo"` → `"S%C3%A3o%20Paulo"`.
- `basicMatchesJdkBase64` — `FxHttp.basic("jmifx","jmifx-secret")` equals `"Basic " + java.util.Base64.getEncoder().encodeToString("jmifx:jmifx-secret".getBytes(UTF_8))`.

- [ ] **Step 2: Run to verify failure** — `xvfb-run -a ./gradlew :jmifx:test --tests 'FxHttpTest'` → compile FAILURE.

- [ ] **Step 3: Implement per the spike verdict**

Variant A: one transport using `java.net.HttpURLConnection` (API-identical on JVM and TeaVM emul). Read status + body (error stream on ≥400), try/catch → `onFailure`, every callback via `Platform.runLater`. Base64 via `java.util.Base64` (spike-verified; fallback: hand-rolled encoder, ledger ruling). Variant B: facade + `ServiceLoader`, tests add `testImplementation project(':jmifx-http-jvm')`.

- [ ] **Step 4: Run to verify pass** — same command, PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: FxHttp dual-platform POST with OAuth support and FxAuth session"`

### Task 4: `FxNavigation` — controller-usable navigation

**Files:**
- Create: `jmifx/src/main/java/com/jmifx/FxNavigation.java`
- Modify: `jmifx/src/main/java/com/jmifx/FxApplication.java` (bind after navigator construction, before startup navigate)
- Test: `jmifx/src/test/java/com/jmifx/FxNavigationTest.java`

**Interfaces:**
- Consumes: `FxNavigator.navigateTo(String)`, `FxViewRegistry.register(String, Supplier<FxView>)`.
- Produces: `public final class FxNavigation` with `public static void navigateTo(String viewId)` delegating to the bound navigator; package-private `static void bind(FxNavigator)` (volatile). Unbound call → `IllegalStateException("FxNavigation not bound — FxApplication.start must run first")`. Used by Tasks 8–9.

- [ ] **Step 1: Write the failing tests**

`FxNavigationTest` (`FxTestKit.start()`; `FxNavigation.bind(null)` in `@AfterEach`):
- `boundNavigatorReceivesNavigation` — facade navigation installs the view root.
- `unboundNavigationThrowsIllegalState`.
- `applicationStartBindsNavigator` — minimal `FxApplication` subclass (startup view `v`, registry with `v` and `other`); `start(stage)` inside `Platform.runLater` + latch; then `FxNavigation.navigateTo("other")` swaps `stage.getScene().getRoot()`'s (the content `StackPane`) single child to the `other` view root.

- [ ] **Step 2: Run to verify failure** — `xvfb-run -a ./gradlew :jmifx:test --tests 'FxNavigationTest'` → compile FAILURE.

- [ ] **Step 3: Implement** — facade + bind; `FxApplication.start` calls `FxNavigation.bind(navigator)` before `navigateTo(getStartupViewId())`.

- [ ] **Step 4: Run to verify pass** — same command PASS; then full `xvfb-run -a ./gradlew :jmifx:test`.

- [ ] **Step 5: Commit** — `git commit -m "feat: FxNavigation static facade bound by FxApplication"`

### Task 5: Codegen — `PasswordField` support

**Files:**
- Modify: `jmifx-gradle/codegen/src/main/java/com/jmifx/codegen/parser/FxmlParser.java` and/or `emit/ViewClassWriter.java` (element registry + emission — wherever TextField is handled)
- Create: `jmifx-gradle/codegen/src/test/resources/fxml/valid/login-view.fxml` (mirror existing fixture naming)
- Create: `jmifx-gradle/codegen/src/test/resources/golden/LoginView.java.txt`
- Modify: `jmifx-gradle/codegen/src/test/java/com/jmifx/codegen/EmissionTest.java`, `FxmlViewCompilerTest.java` (and fixture controller under `javafixture/` per existing pattern)
- Modify: `AGENTS.md` (FXML subset line gains `PasswordField`)

**Interfaces:**
- Produces: `PasswordField` accepted as element and as `@FXML` field type; supports `fx:id, id, text, promptText, prefWidth, prefHeight` (same rule as TextField); emitted like TextField (`new PasswordField()`, setters). `login-view.fxml` → generated class `LoginView` (view id `login-view`). Task 8 depends on this.

- [ ] **Step 1: Write the failing tests**

New fixture `login-view.fxml` (VBox with `usernameField` TextField, `passwordField` PasswordField, `loginButton` Button, `statusLabel` Label + fixture controller `LoginViewController` with package-visible fields) + `EmissionTest` case asserting the `LoginView.java.txt` golden; `FxmlViewCompilerTest` case: PasswordField accepted as controller field type, and PasswordField with an unsupported attribute (e.g. `onAction`) → `file:line` error.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew -p jmifx-gradle :codegen:test`
Expected: FAIL — `login-view.fxml: N` unknown element `PasswordField`.

- [ ] **Step 3: Implement** — add PasswordField wherever TextField is registered (parser whitelist, controller-field type check, emitter constructor/setters). No new behavior beyond aliasing the TextField path.

- [ ] **Step 4: Run to verify pass**

Run: `xvfb-run -a ./gradlew -p jmifx-gradle :codegen:test` (compile-IT needs the display) → PASS, all existing goldens untouched.

- [ ] **Step 5: Update `AGENTS.md` subset line and commit** — `git commit -m "feat: codegen PasswordField support"` (codegen files + goldens + fixtures + AGENTS.md).

### Task 6: Demo server — `City` entity on H2 file store

**Files:**
- Modify: `jmifx-demo/build.gradle` (add `implementation 'io.jmix.data:jmix-eclipselink-starter'`; add `testImplementation 'org.springframework.boot:spring-boot-starter-test'`)
- Modify: `jmifx-demo/src/main/resources/application.yml` (replace the whole `spring.datasource` block with `main:` datasource/liquibase)
- Create: `jmifx-demo/src/main/java/com/jmifx/demo/entity/City.java`
- Create: `jmifx-demo/src/main/resources/com/jmifx/demo/liquibase/changelog.xml`
- Create: `jmifx-demo/src/main/resources/com/jmifx/demo/liquibase/010-create-city.xml`
- Test: `jmifx-demo/src/test/java/com/jmifx/demo/CityPersistenceTest.java`

**Interfaces:**
- Produces: `City` — `@JmixEntity @Entity @Table(name = "CITY")`, fields `UUID id` (`@JmixGeneratedValue @Id @Column(name="ID", nullable=false)`), `Integer version` (`@Version @Column(name="VERSION", nullable=false)`), `String name` (`@InstanceName @NotNull @Column(name="NAME", nullable=false)`) + getters/setters. Consumed by Task 7 (role, REST) and Task 10 (H2 query).

- [ ] **Step 1: Write the failing test**

`CityPersistenceTest`: `@SpringBootTest` — `dataManager.create(City.class)` + name `"Berlin"` → `save` → reload by id → same name, `getVersion() == 1`.

- [ ] **Step 2: Run to verify failure** — `./gradlew :jmifx-demo:test` → context-boot FAILURE (no store wiring).

- [ ] **Step 3: Wire the store and entity**

`application.yml` (replace `spring.datasource`; Jmix 3 convention):
```yaml
main:
  datasource:
    url: jdbc:h2:file:.jmix/h2/demo
    username: sa
    password: ""
  liquibase:
    change-log: com/jmifx/demo/liquibase/changelog.xml
```
Entity per Interfaces. `changelog.xml` includes `010-create-city.xml`: `createTable CITY` — `ID UUID NOT NULL PK`, `VERSION INT NOT NULL`, `NAME VARCHAR(255) NOT NULL`. H2 stays `runtimeOnly`. If `liquibase-core` is missing at boot, add it (ledger ruling).

- [ ] **Step 4: Run to verify pass** — `./gradlew :jmifx-demo:test` PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: City entity on H2 file store with liquibase"`

### Task 7: Demo server — users, authorization server, secured REST

**Files:**
- Modify: `jmifx-demo/build.gradle` — add `implementation 'io.jmix.authserver:jmix-authserver-starter'` and `implementation 'io.jmix.security:jmix-security-data-starter'` (verify exact artifact ids on the Jmix repo if these miss; ledger ruling)
- Create: `jmifx-demo/src/main/java/com/jmifx/demo/entity/User.java`
- Create: `jmifx-demo/src/main/java/com/jmifx/demo/security/DatabaseUserRepository.java`
- Create: `jmifx-demo/src/main/java/com/jmifx/demo/security/CityRestRole.java`
- Create: `jmifx-demo/src/main/resources/com/jmifx/demo/liquibase/020-init-user.xml`
- Modify: `jmifx-demo/src/main/resources/com/jmifx/demo/liquibase/changelog.xml` (add include for `020-init-user.xml`)
- Modify: `jmifx-demo/src/main/resources/application.yml` (authserver client registration + in-memory authorization service)
- Test: `jmifx-demo/src/test/java/com/jmifx/demo/CityRestApiTest.java`

**Interfaces:**
- Consumes: `City` (Task 6).
- Produces: working `POST /oauth2/token` (password grant, Basic `jmifx`:`jmifx-secret`, users `admin`/`admin`) and `POST /rest/entities/City` with Bearer → 201. Consumed by Task 8 (client login constants must match) and Task 10.
- `User` — Studio-template shape: `@JmixEntity @Entity @Table(name = "USER_")` implementing `JmixUserDetails`; fields id (UUID, `@JmixGeneratedValue`), version (`@Version`), username (unique, `@Column(name="USERNAME", nullable=false)`), password (`@Column(name="PASSWORD")`), enabled (`Boolean`, default true) + getters/setters + the `JmixUserDetails` methods delegating to fields.
- `DatabaseUserRepository` — `@Primary @Component("UserRepository") class DatabaseUserRepository extends AbstractDatabaseUserRepository<User> {}`.
- `CityRestRole` — `@ResourceRole(name="City REST", code="city-rest", scope="API")`; `@EntityPolicy(entityClass=City.class, actions={EntityPolicyAction.CREATE, EntityPolicyAction.READ})` + `@EntityAttributePolicy(entityClass=City.class, attributes="name", action=EntityAttributePolicyAction.MODIFY)`.
- `application.yml` additions:
```yaml
spring:
  security:
    oauth2:
      authorizationserver:
        client:
          jmifx:
            registration:
              client-id: jmifx
              client-secret: "{noop}jmifx-secret"
              authorization-grant-types: password
              client-authentication_methods: client_secret_basic
            token:
              access-token-format: reference
              access-token-time-to-live: 1h
jmix:
  authserver:
    use-in-memory-authorization-service: true
```

**Known open points (resolve in Step 1, record rulings):** (a) exact `jmix-security-data` artifact id; (b) the role-assignment table/columns for granting `admin` the `rest-minimal` + `city-rest` resource roles (mirror the `RoleAssignment` entity from `jmix-security-data` — inspect the jar/sources); (c) the BCrypt hash of `admin` — generate once via a scratch run using Spring Security's `BCryptPasswordEncoder` (spring-security-crypto is on the classpath) and paste the literal into `020-init-user.xml`. If any of these turns out disproportionate (e.g. role assignment has no stable table contract), **STOP and ask the human** — the custom-controller fallback remains the spec's escape hatch.

- [ ] **Step 1: Verify the flow by hand first**

Add deps/classes/config; `020-init-user.xml`: create `USER_` + insert `admin` row (enabled, BCrypt hash) + role-assignment rows (`username=admin`, role codes `rest-minimal`, `city-rest`, resource type). `./gradlew :jmifx-demo:bootRun`; then:
`curl -i -X POST http://localhost:8080/oauth2/token --basic --user jmifx:jmifx-secret -H 'Content-Type: application/x-www-form-urlencoded' -d 'grant_type=password' -d 'username=admin' -d 'password=admin'` → 200 + `access_token`;
`curl -i -X POST http://localhost:8080/rest/entities/City -H "Authorization: Bearer <token>" -H 'Content-Type: application/json' -d '{"name":"São \"Paulo\""}'` → 201 + row. Iterate until both hold.

- [ ] **Step 2: Write the failing tests**

`CityRestApiTest` (`@SpringBootTest` + MockMvc via `spring-boot-webmvc-test`; Jackson allowed server-side):
- `passwordGrantAndCreateCityReturns201` — token request as above → 200; extract `access_token`; POST `/rest/entities/City` with Bearer → 201; DataManager load by returned id has the name (pins REST entity name `City`).
- `createWithoutTokenIsUnauthorized` — POST `/rest/entities/City`, no header → 401.
- `wrongPasswordIsRejected` — token request with `password=wrong` → 4xx.

- [ ] **Step 3: Run to verify failure** — `./gradlew :jmifx-demo:test` → FAIL.

- [ ] **Step 4: Make them pass** — apply the Step-1-verified wiring; PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: admin login via password grant and secured City REST create"`

### Task 8: Demo client — login view

**Files:**
- Create: `jmifx-demo-client/src/main/fxml/login-view.fxml`
- Create: `jmifx-demo-client/src/main/java/com/jmifx/demo/client/LoginViewController.java`
- Modify: `jmifx-demo-client/src/main/java/com/jmifx/demo/client/DemoFxApp.java` (startup view `login-view`)

**Interfaces:**
- Consumes: `FxHttp.postForm(url, form, basicAuth, listener)`, `FxHttp.basic("jmifx","jmifx-secret")`, `FxHttp.urlEncode`, `FxJson.stringValue`, `FxAuth.setAccessToken`, `FxNavigation.navigateTo` (Tasks 2–4); `PasswordField` codegen (Task 5); server contract (Task 7).
- Produces: login flow consumed by Task 10 E2E.

- [ ] **Step 1: Author the view and controller**

`login-view.fxml` — VBox (`spacing="10"`, `alignment="CENTER"`, `prefWidth="400"`, `prefHeight="200"`, `fx:controller="com.jmifx.demo.client.LoginViewController"`): Label "Login"; TextField `usernameField` (prompt "Username"); PasswordField `passwordField` (prompt "Password"); Button `loginButton` ("Sign in", `onAction="#login"`); Label `statusLabel` (empty). Controller (package-visible fields): blank username or password → `statusLabel "Username and password required"`, return; disable `loginButton`; `FxHttp.postForm("/oauth2/token", "grant_type=password&username=" + urlEncode(u) + "&password=" + urlEncode(p), FxHttp.basic("jmifx", "jmifx-secret"), listener)`; `onResult` 200 → `FxAuth.setAccessToken(FxJson.stringValue(body, "access_token"))` + `FxNavigation.navigateTo("hello-view")`; else → `"Login failed (HTTP " + statusCode + ")"`; `onFailure` → `"Login failed: " + t.getMessage()`; both re-enable the button. `DemoFxApp.getStartupViewId()` → `"login-view"`.

- [ ] **Step 2: Build and verify codegen**

Run: `./gradlew :jmifx-demo-client:assemble`
Expected: SUCCESS; `build/generated/fxviews/com/jmifx/demo/client/LoginView.java` exists; `FxViewsIndex` registers `login-view`, `hello-view`. Failures are subset violations with `file:line` — fix the FXML, never the codegen.

- [ ] **Step 3: Commit** — `git commit -m "feat: JavaFX login view with password grant"`

### Task 9: Demo client — city detail view + navigation

**Files:**
- Create: `jmifx-demo-client/src/main/fxml/city-detail-view.fxml`
- Create: `jmifx-demo-client/src/main/java/com/jmifx/demo/client/CityDetailViewController.java`
- Modify: `jmifx-demo-client/src/main/fxml/hello-view.fxml` (add City button)
- Modify: `jmifx-demo-client/src/main/java/com/jmifx/demo/client/HelloViewController.java` (add handler)

**Interfaces:**
- Consumes: `FxHttp.post` (auto-Bearer), `FxJson.obj`, `FxNavigation.navigateTo` (Tasks 2–4); server contract (Task 7).
- Produces: the E2E flow of Task 10.

- [ ] **Step 1: Author the view and controllers**

`city-detail-view.fxml` — VBox (same box attributes, controller `CityDetailViewController`): Label "City name"; TextField `cityField` (prompt "Enter city name"); HBox with Button `saveButton` ("Save", `onAction="#save"`) and Button `backButton` ("Back", `onAction="#back"`); Label `statusLabel`. Controller: `save` — blank/whitespace → `"City name required"`, return; disable button; `FxHttp.post("/rest/entities/City", FxJson.obj("name", cityField.getText().trim()), listener)`; 2xx → `"Saved: " + name`; else → `"Save failed (HTTP " + statusCode + ")"`; `onFailure` → `"Save failed: " + t.getMessage()`; both re-enable. `back` → `navigateTo("hello-view")`. Hello-view gains `Button cityButton` ("City", `onAction="#openCityDetail"`) → `navigateTo("city-detail-view")`; Greet flow untouched.

- [ ] **Step 2: Build and verify codegen**

Run: `./gradlew :jmifx-demo-client:assemble`
Expected: SUCCESS; `CityDetailView.java` generated; index has all three views; `compileJava` clean (no Spring/Jmix imports client-side).

- [ ] **Step 3: Commit** — `git commit -m "feat: city detail view with save and navigation"`

### Task 10: E2E acceptance

**Files:**
- Create: `docs/superpowers/e2e/m2-acceptance.md`
- Create: `docs/superpowers/e2e/m2-browser.png` (and `m2-saved.png`, `m2-login-error.png`)

**Interfaces:**
- Consumes: everything above; the H2 jar in the Gradle cache for row checks.

- [ ] **Step 1: Boot and drive the browser**

`./gradlew :jmifx-demo:bootRun` (background). agent-browser → `http://localhost:8080/fx/`: (1) login view renders (masked password input); (2) wrong password → `Login failed (HTTP …)`, stays on login view; (3) `admin`/`admin` → hello view; (4) click **City** → detail view; (5) empty name + **Save** → `City name required`; (6) type `São "Paulo"` → **Save** → `Saved: São "Paulo"`; (7) console clean. Screenshots of login error, detail, saved states.

- [ ] **Step 2: Verify the row survived (file DB)**

Stop the app; `java -cp <h2-jar-from-gradle-cache> org.h2.tools.Shell -url 'jdbc:h2:file:.jmix/h2/demo' -user sa -password '' -sql "select name, version from CITY"` → the row, `version = 1`; also `select username from USER_` → `admin`. Restart the app, re-query CITY → row persists.

- [ ] **Step 3: Write `m2-acceptance.md`** — mirror `m1-acceptance.md`: criteria vs spec §3 flow + §7 error handling, environment, fixes made during E2E, known limitations. PASS only if every criterion holds.

- [ ] **Step 4: Full-suite verification**

Run: `xvfb-run -a ./gradlew build && ./gradlew -p jmifx-gradle test` → all green.

- [ ] **Step 5: Commit** — `git commit -m "docs: milestone 2 e2e acceptance results"`

---

## Self-review notes (already applied)

- Spec coverage: §2 auth/password-grant/admin → Tasks 7–8; §2 PasswordField → Task 5; §4.1–4.4 blocks → Tasks 2–4; §5 views → Tasks 8–9; §6 server → Tasks 6–7; §7 → Tasks 3, 8–10; §8 matrix → each task; §9 risks → Task 1 (transport, Base64, getProperty), Task 7 open points, Task 7 test (entity name), Task 3 (Bearer). §10 deferred → excluded.
- Type consistency: `Listener`, `postForm(url, formBody, basicAuth, listener)`, `basic(String,String)`, `urlEncode(String)`, `FxJson.obj/stringValue`, `FxAuth.setAccessToken/getAccessToken/clear`, `FxNavigation.navigateTo` used identically in Tasks 3, 8, 9.
- Proportion: code blocks only where the spec pins exact values (yaml, curl flow); everything else is signatures and test names.
