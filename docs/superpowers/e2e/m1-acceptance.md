# Milestone 1 — E2E Acceptance Results

**Date:** 2026-10-07 · **Result: PASS** · Acceptance flow of spec §9 verified.

## Acceptance criteria

| # | Criterion (spec §9) | Result |
|---|---|---|
| 1 | `./gradlew :jmifx-demo:bootRun` starts the Jmix 3.0.3 app | ✅ `Started DemoApplication` on :8080 |
| 2 | `http://localhost:8080/fx/` serves the compiled client | ✅ 200, loader page + app.wasm |
| 3 | View renders as WebAssembly with the 3 components | ✅ Label ("Hello!"), TextField (prompt "Type your name"), Button ("Greet") via wasmGC |
| 4 | Typing a name and clicking Greet updates the label | ✅ typed `Eduardo` → label `Hello, Eduardo!` |
| 5 | No browser console errors | ✅ console clean |

Screenshot: `docs/superpowers/e2e/m1-browser.png` · Browser: agent-browser (Chromium, CDP).

## Environment

- Gradle 9.5.1, JDK 25 (Liberica via SDKMAN), TeaVM 0.14.1, WebFX Kit 0.1.0-SNAPSHOT
- Jmix 3.0.3 / Spring Boot 4.1.x (starter: webmvc), H2 in-memory

## Fixes made during E2E (Task 11)

- Demo excluded `dev.webfx` from the client dependency: the WebFX Kit leaked onto
  the server classpath via `runtimeOnly` and hibernate-validator's JavaFX value
  extractors crashed on the kit's browser-only emul classes
  (`NoClassDefFoundError: javafx/beans/property/MapProperty`).

## Known limitations / observations

- wasm-gc only — requires a modern browser (spec §2 decision).
- The blank-name branch ("Hello!") is exercised by code inspection only, not E2E.
- Bundle ~1.6 MB unminified (no ADVANCED/minification configured yet — deferred).
- Two textboxes appear in the accessibility tree (WebFX TextField renders an
  extra element); cosmetic, does not affect behavior.
