# jmifx — Milestone 2: city detail view + data building blocks

**Date:** 2026-10-07
**Status:** Approved design (brainstorming complete)
**Target:** builds on the M1 pipeline (spec: `2026-10-06-jmifx-design.md`); Jmix 3.0.3, Spring Boot 4.1, TeaVM 0.14.1, WebFX Kit snapshots

## 1. Purpose

Milestone 1 proved the render pipeline (FXML → codegen → TeaVM → wasm served at `/fx/`). Milestone 2 adds the first **data round trip**: a Jmix-style *detail view* (single-entity editor) with one field — the city name — where clicking Save persists a row to the database through the Jmix server.

Per the project goal (`AGENTS.md`), features are built as **reusable building blocks in the add-on**; `jmifx-demo` only exercises them. This milestone ships two new client-framework blocks — `FxHttp` (dual-platform HTTP/JSON) and `FxNavigation` (view-to-view navigation) — plus the demo wiring that proves them against a real Jmix REST surface.

## 2. Decisions (from brainstorming, approved)

| Decision | Choice | Rationale |
|---|---|---|
| REST surface | **Generic REST API (`io.jmix.rest:jmix-rest-starter`) + anonymous access** | Standard Jmix data API — what real jmifx apps will consume. Zero hand-written controllers or persistence code; access expressed as resource roles. Client POSTs to `POST /rest/entities/City`. |
| REST DataStore (`jmix-restds`) | **Rejected** | It lets a Jmix *server* app consume a *remote* Jmix app's REST API via `DataManager` DTO entities. It is a Spring/server-side mechanism and cannot run in the wasm client (hard constraint: zero Spring/Jmix imports client-side). Solves Jmix↔Jmix integration, not browser→own-server. Only revisit if jmifx ever proxies a second Jmix app server-side. |
| Persistence API | **DataManager principle — but no app persistence code exists in this milestone** | Jmix docs: use `DataManager`, fall back to `EntityManager` only when really needed (EntityManager skips entity events, bypasses access control). With the generic REST API the add-on persists internally; if the Approach-2 fallback is ever triggered, that custom code uses `DataManager`. |
| Database | **H2, file mode** (`jdbc:h2:file:.jmix/h2/demo`) | Already on the demo classpath; file mode makes saved rows survive restarts so "it saved" is demonstrable. Liquibase changelog creates the table (Jmix-idiomatic). |
| Client HTTP | **`FxHttp` building block in `jmifx`**; transport decided by opening spike — (a) TeaVM-emulated `java.net.HttpURLConnection`, else (b) `FxHttpTransport` SPI via `ServiceLoader` (elemental2 `fetch` impl for wasm, `java.net.http` impl for JVM) | One block must serve JVM and wasm. (a) gives a single implementation; (b) follows the WebFX compileOnly/teavmClasspath selection pattern. Zero reflection either way. |
| Navigation | **Second view reached from hello-view + Back button; `FxNavigation` static facade** | Exercises `FxNavigator` beyond startup (it is currently created but unreachable from controllers). Mirrors Jmix's `ViewNavigators` mental model. |
| Auth | **Anonymous access for this milestone** (`jmix.resource-server.anonymous-url-patterns` + API-scope resource role) | M1 spec staged auth for later; the OAuth token dance in wasm is its own milestone. |
| Detail view mode | **Create-only** | No list view, no load/edit of existing entities. YAGNI. |

## 3. Architecture & data flow

```
Browser (wasm, /fx/)                       Jmix server (jmifx-demo)
┌─────────────────────────────┐            ┌──────────────────────────────────┐
│ hello-view ──[City]──► FxNavigation      │                                  │
│ city-detail-view            │   POST      │ POST /rest/entities/City        │
│  TextField + Save ──► FxHttp│ ──────────► │  (jmix-rest-starter; anonymous   │
│  (jmifx building block)     │  JSON       │   resource role grants CREATE)   │
│  status label ◄── callback  │ ◄────────── │  → JpaDataStore → H2 (file)      │
└─────────────────────────────┘  201        │  → row in CITY table             │
                                            └──────────────────────────────────┘
```

- Same-origin (the client is served by the same app at `/fx/**`), so no CORS concerns.
- Request body is hand-built JSON (`{"name":"Berlin"}`) via `FxJson`; the 201 response body is not parsed in this milestone (status code only).
- Server side has **no application persistence code**: the REST add-on saves through Jmix's standard machinery; authorization is the anonymous resource role.

## 4. Client building blocks (`jmifx` framework)

### 4.1 `FxHttp`

Async HTTP POST for wasm and JVM, zero reflection, zero Spring/Jmix.

```java
public final class FxHttp {
    public interface Listener {
        void onResult(int statusCode, String body);   // 2xx/4xx/5xx responses
        void onFailure(Throwable t);                  // network-level failures
    }
    public static void post(String url, String jsonBody, Listener listener) { ... }
}
```

- **Callbacks are marshaled to the FX UI thread** (`Platform.runLater`) so controllers may touch controls directly.
- **URL resolution:** relative URLs (e.g. `/rest/entities/City`) resolve against the page origin in the browser; on the JVM against system property `jmifx.base.url` (default `http://localhost:8080`).
- **Transport** (opening spike decides, preference order):
  - **(a)** TeaVM's emulated `java.net.HttpURLConnection` — if available under wasmGC 0.14.1, one implementation runs unchanged on JVM and browser;
  - **(b)** `FxHttpTransport` SPI resolved via `ServiceLoader` (TeaVM processes it at build time): elemental2-`fetch` impl on the wasm classpath, `java.net.http` impl on the JVM/test classpath — same compileOnly + teavmClasspath selection pattern as the WebFX Kit.

### 4.2 `FxJson`

Minimal JSON building for primitives: `FxJson.obj("name", value)` → `{"name":"..."}` with correct string escaping (quotes, backslash, control characters). No reflection, no parser — the milestone only *writes* JSON.

### 4.3 `FxNavigation`

```java
public final class FxNavigation {
    public static void navigateTo(String viewId) { ... }
}
```

A static facade bound to the app's `FxNavigator` during `FxApplication.start()` (the navigator is created there today but unreachable from controllers). Failure semantics unchanged from `FxNavigator` (on-screen error label, never a blank pane).

## 5. Demo client (`jmifx-demo-client`)

- **`city-detail-view.fxml`** — VBox: Label "City name", `cityField` TextField (prompt "Enter city name"), `saveButton` ("Save", `onAction="#save"`), `statusLabel` (initially empty). Uses only the existing FXML subset — **no codegen changes, no new golden files**.
- **`CityDetailViewController`** — `save(ActionEvent)`: reject blank name inline (no request); disable the button while a request is in flight; POST via `FxHttp`; on 2xx → `statusLabel` shows "Saved: \<name\>"; on non-2xx or network failure → error text including the status code/message. A "Back" button navigates to `hello-view` via `FxNavigation`.
- **`hello-view.fxml`** — gains a "City" button (`onAction="#openCityDetail"`) → `FxNavigation.navigateTo("city-detail-view")`. The existing Greet flow is untouched.

## 6. Demo server (`jmifx-demo`)

- **Dependencies to add:** `io.jmix.data:jmix-eclipselink-starter`, `io.jmix.rest:jmix-rest-starter`, plus the **minimal security/resource-server set** that anonymous REST access requires on Jmix 3.0.3 — the exact set is a spike/verification deliverable (the demo currently has no security add-ons; token issuance / Authorization Server is *not* needed for anonymous access).
- **`City` entity** (`com.jmifx.demo.entity.City`): standard Jmix shape — `@JmixGeneratedValue` UUID id, `@Version Integer version`, `@InstanceName @NotNull String name`, `@Table(name = "CITY")`.
- **Resource role** (API scope): grants `CREATE` and `READ` on `City` (attribute policy: `MODIFY` on `name`), assigned to the anonymous user.
- **Properties:** `jmix.resource-server.anonymous-url-patterns=/rest/entities/City/**`; `main.datasource.*` (H2 file), `main.liquibase.change-log`.
- **Liquibase:** `com/jmifx/demo/liquibase/changelog.xml` + initial changeset creating `CITY` (`ID uuid pk`, `VERSION int not null`, `NAME varchar(255) not null`).

## 7. Error handling

- Blank name → inline status message, no request sent.
- In-flight → Save button disabled (no double submits).
- HTTP error or network failure → status label with status code/message; button re-enabled. Never silent.
- Server errors surface as standard Jmix REST responses; the client shows the status code only (error-body parsing deferred).
- Unknown-view navigation failures keep `FxNavigator`'s on-screen error-label behavior.

## 8. Testing

| Layer | Approach |
|---|---|
| `FxHttp` / `FxJson` / `FxNavigation` | JVM unit tests (xvfb): JSON escaping incl. control chars; URL resolution (relative vs absolute, `jmifx.base.url`); POST round-trip against `com.sun.net.httpserver` (JVM transport); listener runs on FX thread |
| Demo server | `@SpringBootTest` + MockMvc (`spring-boot-webmvc-test`): anonymous POST `/rest/entities/City` → 201; row loadable via `DataManager`; role enforcement (entity without policy → 4xx) |
| E2E | Manual browser acceptance: `/fx/` → hello view → City → detail view → type name → Save → "Saved: X"; row present in H2 after restart; screenshot + acceptance note under `docs/superpowers/e2e/` like M1 |

## 9. Risks & fallback

1. **HTTP from wasm unproven** — opening spike in the existing `spike-webfx` build answers (a) vs (b) before any product code.
2. **Anonymous-REST minimal wiring on a core-only app** — spike/verification task pins the exact starter set; if it proves disproportionate, **fallback = Approach 2** (custom `@RestController` + `DataManager`), which changes only the server side and the URL/payload at the call site — `FxHttp`, `FxJson`, `FxNavigation` and both views are identical either way.
3. **TeaVM `ServiceLoader` support** — only on transport route (b); the spike covers it implicitly (route (b) fails without it).
4. **REST entity naming** (bare `City` vs project-prefixed) — verified by the server integration test, not assumed.

## 10. Deferred scope (post-Milestone-2)

City list view, load/edit of existing entities, OAuth login/token handling in the wasm client, error-body parsing, GET/PUT surface on `FxHttp`, JSON *parsing* on the client, URL routing/hash navigation, automated browser E2E.
