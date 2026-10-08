# SDD ledger — plan: docs/superpowers/plans/2026-10-07-city-detail-m2.md

## Setup

- Execution mode: Native (executing-plans), TDD skill loaded.
- Setup: Ruling: branch-in-place on `feature/m2-city-detail` instead of a worktree — M1 precedent (ledger-m1 Setup ruling). Cost if wrong: none material (main is docs + merged M1; the branch isolates).
- Spec read: docs/superpowers/specs/2026-10-07-city-detail-m2-design.md (authority, incl. Change log: login + password grant, no anonymous).
- Baseline: root build + composite build green before Task 1.

## Pre-flight scan (shared interfaces)

- T1→T3 spike verdict drives FxHttp transport; T2/T3/T4 signatures as plan; T5→T8 PasswordField; T6→T7 City + changelog; T7→T8 client constants; T7→T10 H2 queries. No unresolved conflicts.

## Tasks

Task 1: Ruling: transport **route A is dead at runtime** — TeaVM build succeeds with `java.net.HttpURLConnection` reachable, but wasm instantiation traps ("dereferencing a null pointer"); verdict **B**: `@JSBody` fetch bridge + `@JSFunctor` callbacks (int/String only). elemental2 `Promise`/`Response` marshalling rejects with wrapped errors (request itself 200s) — do not chain `elemental2.promise.Promise` in Java. Also verified: `System.getProperty(k,def)` and `java.util.Base64` work in wasm; `ServiceLoader` works via `iterator()` but `findFirst()` is unimplemented (build error). Cost if wrong: FxHttp built on route A would trap every wasm load.
Task 1: Ruling: Task 3 Variant B is **simpler than the plan's file list** — no new `jmifx-http-wasm`/`jmifx-http-jvm` modules and no plugin changes: wasm transport (`FetchBridge` with `@JSBody`, needs only `org.teavm:teavm-jso` compileOnly — no elemental2 types in Java signatures) lives in `jmifx` main with its own `META-INF/services` entry; JVM transport lives in the `jmifx` **test source set** with a test `META-INF/services` entry (proves dual-platform contract; desktop-app module deferred with the desktop story). Selection via `ServiceLoader.load(FxHttpTransport.class)` iterator. Avoids a project-dependency cycle (http-jvm → jmifx vs jmifx test → http-jvm). Cost if wrong: JVM/desktop consumers must later add a real transport module — deferred anyway.
Task 1: complete (commits de2aa64..see-log, tests: buildWasmGC + agent-browser probes → JRE probe `prop=default b64=cHJvYmU=`, POST probe `HTTP 200` + echo body, SL probe `SL found: browser`, console clean)
Task 3: Ruling: implemented Variant B per Task-1 ruling — BrowserHttpTransport (@JSBody bridge, teavm-jso 0.14.1 compileOnly) + META-INF/services in jmifx main; JvmHttpTransport (java.net.http) in the jmifx TEST source set; selection via ServiceLoader iterator with package-private FxHttp.setTransport test seam (avoids provider double-registration in tests). Test-fixture: dead-local-port never fast-refuses on this box (JDK HttpClient connect hangs past latch) — unreachableServerCallsOnFailure uses non-routable 10.255.255.1:81 (connect timeout ~5s) instead. Cost if wrong: flaky failure test only.
