# Milestone 2 — E2E Acceptance Results

**Date:** 2026-10-07/08 · **Result: PASS** · Acceptance flow of spec §3 + error handling §7 verified (login revision per spec Change log).

## Acceptance criteria

| # | Criterion | Result |
|---|---|---|
| 1 | `bootRun` starts the Jmix 3.0.3 app (now with eclipselink, security-data, authserver, rest) | ✅ `Started DemoApplication` on :8080, liquibase runs add-on + app changelogs |
| 2 | `http://localhost:8080/fx/` shows the **login view** with masked password (PasswordField) | ✅ Username field, Password field (renders `•••`), Sign in — via wasmGC |
| 3 | Wrong password → `Login failed (HTTP 400)`, stays on login view | ✅ (screenshot `m2-login-error.png`) |
| 4 | `admin`/`admin` → hello view | ✅ password grant → token → `FxAuth` → navigate |
| 5 | City button → city detail view (Label, TextField, Save, Back) | ✅ (screenshot `m2-detail.png`) |
| 6 | Empty name + Save → `City name required`, no request | ✅ |
| 7 | Type `São "Paulo"` → Save → `Saved: São "Paulo"` (JSON-hostile name intact) | ✅ (screenshot `m2-saved.png`) |
| 8 | Row persisted in H2 **file** DB, survives restart | ✅ `CITY` has the row (version 1); re-queried after full app restart — still present |
| 9 | REST secured: no token → 401; wrong password at token endpoint → 4xx | ✅ pinned by `CityRestApiTest` (server) + verified by curl |
| 10 | No browser console errors | ✅ console clean |

## Environment

- Gradle 9.5.1, JDK 25 (Liberica), TeaVM 0.14.1, WebFX Kit 0.1.0-SNAPSHOT
- Jmix 3.0.3 (core, data/eclipselink, security-data, authserver, rest) / Spring Boot 4.1.1 (webmvc), H2 file (`.jmix/h2/demo`)
- Browser: agent-browser (Chromium, CDP), sessions `m2-spike` / `m2-e2e`

## Fixes made during E2E / server bring-up (details in ledger-m2)

- Jmix 3 needs Studio-template `DataSourceProperties`/`HikariDataSource` beans or it silently uses an embedded H2 mem default; H2 file URLs need an explicit `./` prefix.
- Add-on changelogs are not auto-discovered — the app changelog must `<include>` `io/jmix/securitydata/liquibase/changelog.xml`.
- `initAnonymousUser` must be overridden (empty authorities — anonymous stays unauthorized).
- Stored password needs the `{bcrypt}` prefix for the DelegatingPasswordEncoder.
- `jmix.resource-server.authenticated-url-patterns: /rest/**` is required — without it REST returns 500 ("Authentication is not set") instead of 401 and Bearer tokens are never resolved.

## Known limitations / observations

- agent-browser `fill` appends on WebFX inputs instead of replacing — E2E used keyboard events (`press Control+a` + `keyboard type`) for already-filled fields.
- The wasm HTTP transport is fetch-based (@JSBody bridge, route B); TeaVM-emulated `HttpURLConnection` traps wasm instantiation (route A dead — spike doc).
- OAuth client secret ships in the wasm binary (spec §2 accepted risk); proper public-client flow deferred.
- The dev H2 file DB accumulated rows from curl probes and repeated test runs (Berlin/Springfield) — dev-only artifact.
- Token is memory-only in the client (`FxAuth`); page reload logs the user out (deferred with token persistence).
