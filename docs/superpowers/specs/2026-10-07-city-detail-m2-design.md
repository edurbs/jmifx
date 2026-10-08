# jmifx — Milestone 2: login + city detail view + data building blocks

**Date:** 2026-10-07
**Status:** Approved design (brainstorming complete); revised 2026-10-07 — see Change log
**Target:** builds on the M1 pipeline (spec: `2026-10-06-jmifx-design.md`); Jmix 3.0.3, Spring Boot 4.1, TeaVM 0.14.1, WebFX Kit snapshots

## 1. Purpose

Milestone 1 proved the render pipeline (FXML → codegen → TeaVM → wasm served at `/fx/`). Milestone 2 adds the first **data round trip behind a real login**: a JavaFX login view (Jmix-login-style) and a Jmix-style *detail view* (single-entity editor) with one field — the city name — where clicking Save persists a row to the database through the Jmix generic REST API, authenticated as the default `admin` user.

Per the project goal (`AGENTS.md`), features are built as **reusable building blocks in the add-on**; `jmifx-demo` only exercises them. This milestone ships three new client-framework blocks — `FxHttp` (dual-platform HTTP/JSON with OAuth password-grant support and `FxAuth` token holding), `FxJson` (minimal write + single-value read), and `FxNavigation` (view-to-view navigation) — plus a codegen subset extension (`PasswordField`) and the demo wiring that proves them against a real Jmix REST surface.

## 2. Decisions (from brainstorming, approved)

| Decision | Choice | Rationale |
|---|---|---|
| REST surface | **Generic REST API (`io.jmix.rest:jmix-rest-starter`)** | Standard Jmix data API — what real jmifx apps will consume. Zero hand-written controllers or persistence code; access expressed as resource roles. Client POSTs to `POST /rest/entities/City` with a Bearer token. |
| REST DataStore (`jmix-restds`) | **Rejected** | It lets a Jmix *server* app consume a *remote* Jmix app's REST API via `DataManager` DTO entities. It is a Spring/server-side mechanism and cannot run in the wasm client (hard constraint: zero Spring/Jmix imports client-side). Solves Jmix↔Jmix integration, not browser→own-server. |
| Auth | **JavaFX login view + OAuth2 Resource Owner Password grant; default `admin`/`admin` user. No anonymous access.** | The Authorization Server add-on (`jmix-authserver-starter`) implements the password grant (removed from OAuth 2.1, re-added by Jmix). The login view mirrors Jmix's; the token endpoint is `POST /oauth2/token` (Basic client auth + form body). MVP client registration: id `jmifx`, secret `jmifx-secret`, opaque reference tokens, 1h TTL, in-memory authorization service (avoids the DB-token `User` mixin customizer). The secret ships inside the wasm — accepted for the demo; the proper public-client flow (authorization code + PKCE) is deferred. |
| Persistence API | **DataManager principle — but no application persistence code for the City save** | Jmix docs: use `DataManager`, fall back to `EntityManager` only when really needed (EntityManager skips entity events, bypasses access control). The generic REST API persists internally; demo/test server code that does persist uses `DataManager`. |
| Database | **H2, file mode** (`jdbc:h2:file:.jmix/h2/demo`) | Already on the demo classpath; file mode makes saved rows survive restarts. Liquibase changelogs create `USER_`, role-assignment rows, and `CITY` (Jmix-idiomatic seeding, mirroring Studio's `010-init-user.xml`). |
| Client HTTP | **`FxHttp` building block in `jmifx`**; transport decided by opening spike — (a) TeaVM-emulated `java.net.HttpURLConnection`, else (b) `FxHttpTransport` SPI via `ServiceLoader` (elemental2 `fetch` impl for wasm, `java.net.http` impl for JVM) | One block must serve JVM and wasm. (a) gives a single implementation; (b) follows the WebFX compileOnly/teavmClasspath selection pattern. Zero reflection either way. |
| Navigation | **Login view is the startup view; hello-view reached after login; city detail from hello-view + Back. `FxNavigation` static facade** | Exercises `FxNavigator` beyond startup. Mirrors Jmix's login → main → detail flow and `ViewNavigators` mental model. |
| FXML subset | **+ `PasswordField`** (codegen + goldens) | A login view needs masked input; `PasswordField` extends `TextField`, so the emitter change is small. AGENTS.md subset list updated with it. |
| Detail view mode | **Create-only** | No list view, no load/edit of existing entities. YAGNI. |

## 3. Architecture & data flow

```
Browser (wasm, /fx/)                       Jmix server (jmifx-demo)
┌──────────────────────────────┐           ┌───────────────────────────────────────┐
│ login-view ──Login──► FxHttp │  POST     │ POST /oauth2/token                    │
│  (FxAuth.setAccessToken)     │ ────────► │  Basic jmifx:jmifx-secret             │
│        ▼ FxNavigation       │  form      │  grant_type=password&username=admin…  │
│ hello-view ──City──► detail  │ ◄──────── │  → {"access_token": …}                │
│ detail: Save ──► FxHttp      │  200      │                                       │
│  (Authorization: Bearer …)   │  POST     │ POST /rest/entities/City              │
│  status label ◄── callback   │ ────────► │  Bearer token → admin roles:          │
└──────────────────────────────┘  JSON/201 │  → rest-minimal + city-rest (API)     │
                                            │  → JpaDataStore → H2 (file) → CITY    │
                                            └───────────────────────────────────────┘
```

- Same-origin (client served by the same app), so no CORS.
- Login POSTs the password grant; the response's `access_token` is kept in `FxAuth` and attached automatically as `Authorization: Bearer …` by `FxHttp` on subsequent JSON POSTs.
- The save body is hand-built JSON (`{"name":"Berlin"}`) via `FxJson`; the 201 body is not parsed (status code only).
- Server side has **no application persistence code for the City save**: the REST add-on persists through Jmix's standard machinery; authorization = the admin user's API-scope resource roles (`rest-minimal`, `city-rest`).

## 4. Client building blocks (`jmifx` framework)

### 4.1 `FxHttp`

Async HTTP for wasm and JVM, zero reflection, zero Spring/Jmix.

```java
public final class FxHttp {
    public interface Listener {
        void onResult(int statusCode, String body);   // any HTTP response
        void onFailure(Throwable t);                  // network-level failures
    }
    public static void post(String url, String jsonBody, Listener listener) { ... }
    public static void postForm(String url, String formBody, String basicAuth, Listener listener) { ... }
    public static String basic(String username, String password) { ... }   // "Basic …" header value
    public static String urlEncode(String s) { ... }
}
```

- **Callbacks marshaled to the FX UI thread** (`Platform.runLater`).
- **URL resolution:** absolute URLs pass through; relative URLs are prefixed with `System.getProperty("jmifx.base.url", "http://localhost:8080")` (guarded — TeaVM may not implement properties). Same-origin by default → CORS-free.
- `post` sends JSON and **auto-attaches `Authorization: Bearer <token>` when `FxAuth` holds a token**; `postForm` sends `application/x-www-form-urlencoded` with the optional `basicAuth` header and no auto-Bearer (used for the token endpoint itself).
- **Transport** (opening spike decides, preference order): (a) TeaVM-emulated `java.net.HttpURLConnection` — one implementation on JVM and browser; (b) `FxHttpTransport` SPI via `ServiceLoader` with elemental2-`fetch` / `java.net.http` impls on the respective classpaths.

### 4.2 `FxJson`

Minimal JSON: `FxJson.obj(String key, String value)` → `{"key":"value"}` with full string escaping (quotes, backslash, `\u0000`–`\u001F`); `FxJson.stringValue(String json, String key)` → the unescaped value of a top-level string member, or `null` (used to read `access_token`). No general parser, no reflection.

### 4.3 `FxAuth`

Static session holder: `getAccessToken()`, `setAccessToken(String)`, `clear()`, `isLoggedIn()`. Memory-only (no browser storage in this milestone). `FxHttp.post` consults it; nothing else does.

### 4.4 `FxNavigation`

`FxNavigation.navigateTo(String viewId)` — static facade bound to the app's `FxNavigator` during `FxApplication.start()`. Failure semantics unchanged from `FxNavigator` (on-screen error label, never a blank pane).

## 5. Demo client (`jmifx-demo-client`)

- **`login-view.fxml`** (startup view) — VBox: Label "Login", `usernameField` TextField (prompt "Username"), `passwordField` PasswordField (prompt "Password"), `loginButton` ("Sign in", `onAction="#login"`), `statusLabel` (empty).
- **`LoginViewController`** — blank fields → inline "Username and password required", no request; disable button in flight; `FxHttp.postForm("/oauth2/token", "grant_type=password&username=…&password=…" (url-encoded), FxHttp.basic("jmifx", "jmifx-secret"), …)`; on 200 → `FxAuth.setAccessToken(FxJson.stringValue(body, "access_token"))` → `FxNavigation.navigateTo("hello-view")`; on 4xx/failure → "Login failed (HTTP …)" / "Login failed: …". 
- **`city-detail-view.fxml`** — VBox: Label "City name", `cityField` TextField, `saveButton` ("Save", `onAction="#save"`), `backButton` ("Back", `onAction="#back"`), `statusLabel`. Existing subset + PasswordField only.
- **`CityDetailViewController`** — blank name → "City name required"; disable button; `FxHttp.post("/rest/entities/City", FxJson.obj("name", …), …)` (Bearer automatic); 2xx → "Saved: \<name\>"; else error with status code; Back → `hello-view`.
- **`hello-view.fxml`** — gains a "City" button → `FxNavigation.navigateTo("city-detail-view")`. Greet flow untouched.

## 6. Demo server (`jmifx-demo`)

- **Dependencies:** `jmix-eclipselink-starter`, `jmix-rest-starter` (pulls `jmix-security-resource-server`), `jmix-authserver-starter`, `jmix-security-data-starter` (DB users + role assignments; exact artifact names verified during implementation).
- **`User` entity** (`com.jmifx.demo.entity.User`, Studio-template shape): JPA entity implementing `JmixUserDetails` — id, version, username, password (hashed), enabled.
- **`DatabaseUserRepository`** — `@Primary @Component` extending `AbstractDatabaseUserRepository<User>` (empty body is enough for this milestone).
- **`CityRestRole`** — `@ResourceRole(name="City REST", code="city-rest", scope="API")`: `@EntityPolicy(City, {CREATE, READ})` + `@EntityAttributePolicy(City, "name", MODIFY)`.
- **Liquibase:** `010-create-city.xml` (CITY) and `020-init-user.xml` (USER_ table + `admin` row with BCrypt hash of `admin`, plus role-assignment rows granting `admin` the `rest-minimal` and `city-rest` resource roles — table/columns mirror the `RoleAssignment` entity, verified during implementation). BCrypt hash generated once with the app's `PasswordEncoder` and pasted into the changeset.
- **Properties:** the authserver client registration (id `jmifx`, secret `{noop}jmifx-secret`, grants `password`, `client_secret_basic`, opaque reference tokens, 1h TTL) and `jmix.authserver.use-in-memory-authorization-service: true`; H2 file datasource + Liquibase changelog under `main.*`. **No anonymous URL patterns.**

## 7. Error handling

- Login: blank fields → inline message, no request; wrong credentials (4xx) → "Login failed (HTTP …)"; network failure → "Login failed: …". Never navigates on failure.
- Save: blank name → inline message, no request; in-flight → button disabled; non-2xx (incl. 401 expired token) → "Save failed (HTTP …)"; network failure → error text. Never silent.
- Server errors surface as status codes only (error-body parsing deferred). Unknown-view navigation keeps `FxNavigator`'s on-screen error label.

## 8. Testing

| Layer | Approach |
|---|---|
| `FxJson` | Plain JUnit: escaping (quotes/backslash/newline/control chars), `obj` shapes, `stringValue` extraction incl. missing key |
| `FxHttp`/`FxAuth` | JVM tests (xvfb): JSON POST round-trip via `com.sun.net.httpserver`; form POST with Basic header observed server-side; auto-Bearer attached iff token set; 500 → `onResult(500)`; refused connection → `onFailure`; listener on FX thread; `resolveUrl` cases; `basic()`/`urlEncode()` values |
| `FxNavigation` | JVM tests: bound delegation, unbound → `IllegalStateException`, `FxApplication.start` binds |
| Codegen | PasswordField: golden files + error fixtures (unchanged harness) |
| Demo server | `@SpringBootTest` + MockMvc: password grant with `admin`/`admin` → token; POST `/rest/entities/City` with Bearer → 201 + row via DataManager; without token → 401; wrong password → 4xx |
| E2E | Browser: wrong password → error, stays on login; `admin`/`admin` → hello → City → Save → "Saved: …"; row in H2 after restart; screenshot + acceptance note |

## 9. Risks & fallback

1. **HTTP from wasm unproven** — opening spike answers transport (a) vs (b), plus `System.getProperty` and `java.util.Base64` behavior under TeaVM (both used by `FxHttp`; fallbacks: guarded default, hand-rolled Base64).
2. **Security-data wiring details** (exact artifact names, `RoleAssignment` table shape, BCrypt hash) — verified by hand-first curl cycle in the server task; anything disproportionate escalates to the human (custom-controller fallback remains documented).
3. **TeaVM `ServiceLoader`** — only on transport route (b); covered by the spike.
4. **REST entity naming** (bare `City` vs prefixed) — pinned by the server test.
5. **Client secret visible in the wasm binary** — accepted for the demo; the real browser-client flow (authorization code + PKCE public client) is deferred with logout/refresh.

## 10. Deferred scope (post-Milestone-2)

Built-in framework login view (Jmix-style building block in `jmifx`, auto-registered — login currently lives in the demo client), refresh tokens, logout, remember-me/token persistence (browser storage), authorization-code + PKCE public client, city list view, load/edit of existing entities, error-body parsing, GET/PUT on `FxHttp`, general JSON parsing, URL routing/hash navigation, automated browser E2E, page-origin URL detection for cross-host deployments.

## Change log

- **2026-10-07:** Auth decision replaced — anonymous REST access dropped in favor of a JavaFX login view + OAuth2 password grant with the default `admin` user (per partner review). Added `PasswordField` to the FXML subset; added `FxAuth`; `FxHttp` gained `postForm`/`basic`/`urlEncode` and auto-Bearer; `FxJson` gained `stringValue`; server gains authserver + security-data + user/role seeding. No other decisions changed.
