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
Task 7: Ruling: Jmix 3 needs Studio-template datasource beans (DataSourceProperties @ConfigurationProperties("main.datasource") + HikariDataSource; Boot 4 package org.springframework.boot.jdbc.autoconfigure) — without them Jmix silently falls back to an embedded H2 mem default. H2 file URLs require explicit "./" prefix. Cost if wrong: file DB silently ignored.
Task 7: Ruling: add-on changelogs are NOT auto-discovered — the app changelog must explicitly <include> io/jmix/securitydata/liquibase/changelog.xml before app changesets. Cost if wrong: SEC_* tables missing, boot fails on role-assignment inserts.
Task 7: Ruling: DatabaseUserRepository must override initAnonymousUser (empty authorities — keeps anonymous fully unauthorized; base class leaves authorities null → NPE at filter-chain build). Cost if wrong: boot crash.
Task 7: Ruling: stored password needs the {bcrypt} prefix (DelegatingPasswordEncoder rejects bare hashes with 500); jmix.resource-server.authenticated-url-patterns=/rest/** is required — without it REST calls 500 "Authentication is not set" instead of 401 and Bearer tokens are never resolved. Cost if wrong: unusable auth surface.
Task 7: Ruling: tests verify persistence via UnconstrainedDataManager (withSystem/withUser hit API/UI scope denials: system has no roles, admin's city-rest is API-scope); authorization itself is pinned by the REST assertions (201/401/4xx). Also: dev H2 file DB wiped (rm -rf jmifx-demo/.jmix) twice during wiring — dev-only, replays via liquibase.
