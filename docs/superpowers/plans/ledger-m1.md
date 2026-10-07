# SDD ledger — plan: docs/superpowers/plans/2026-10-06-jmifx-milestone1.md

## Setup

- Execution mode: Native (executing-plans), TDD skill loaded.
- Setup: Ruling: work in place on `feature/m1-pipeline` instead of a separate worktree — repo is 2 commits old, contains only docs, no work to protect; a worktree would fight the session working directory. Cost if wrong: none material (main has no source).
- Spec read: docs/superpowers/specs/2026-10-06-jmifx-design.md (authority).

## Pre-flight scan (shared interfaces)

- T4→T5/T6/T7: `FxmlViewCompiler.compile(List<Path>, Path)`, `CompilationResult`, `FxmlCompileError.format()` — signatures identical in all consumers. Clean.
- T3→T5/T6/T10: `FxView { String getId(); Parent getRoot(); }`, `FxViewRegistry.register(String, Supplier<FxView>)`, `FxApplication { getStartupViewId(); registerViews(FxViewRegistry) }` — consistent with generated-code usage and demo. Clean.
- T5→T10: `com.jmifx.generated.FxViewsIndex.registerAll(FxViewRegistry)` — consistent. Clean.
- T7→T8: extension property `mainClass` defined T7, consumed T8/T10. Clean.
- T2→T8/T10: spike doc supplies exact WebFX artifact list + TeaVM DSL — values unknowable until spike runs (by design); consumers read the doc. Clean.
- T8→T10: packaging output `build/jmifx-web/jmifx-web/` registered as resources srcDir ↔ T10 jar assertion `BOOT-INF/classes/jmifx-web/index.html`. Clean.
- T6 composite-build limitation (`project(':jmifx')` impossible from included build) — plan already resolves via local FxView stub. Clean.

Pre-flight: no unresolved conflicts.

## Tasks

Task 2: Ruling: compile client modules against WebFX Kit emul jars, not org.openjfx — Gradle ignores openjfx's Maven profiles (empty jars) and kit IS the API TeaVM compiles; spike-proven. Cost if wrong: API drift between compile and wasm classpath.
Task 2: Ruling: FxApplication launches via WebFxKitLauncher.launchApplication(App::new, args) — Application.launch() is stubbed in the kit and fails the build; affects Task 3 (add dev.webfx:webfx-kit-launcher compileOnly) and Task 8 (teavm task = buildWasmGC, output build/generated/teavm/wasm-gc/). Cost if wrong: non-launching apps / wrong task wiring in plugin.
Task 4: Ruling: renamed valid fixture valid-hello.fxml → hello-view.fxml — view id derives from file name (spec §6) and Task 5's golden files pin class HelloView; the plan's fixture filename was the typo. Cost if wrong: golden-file names in Task 5 need renaming too.
Task 5: Ruling: generatedClassName() appends "View" only when PascalCase(id) doesn't already end with "View" — spec §6's own example (hello-view.fxml → HelloView) contradicts the plan's literal "always append" rule; no-doubling matches the spec example. Cost if wrong: file names must avoid "-view" suffix.
Task 1: complete (commits 0908bff..8c0757a, tests: ./gradlew projects --console=plain → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.1/userguide/configuration_cache_enabling.html)
Task 2: complete (commits 8c0757a..8fdc825, tests: ./gradlew -p spike-webfx buildWasmGC --console=plain → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.1/userguide/configuration_cache_enabling.html)
Task 3: complete (commits 8fdc825..1650415, tests: xvfb-run -a ./gradlew :jmifx:test --console=plain → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.1/userguide/configuration_cache_enabling.html)
Task 4: complete (commits 1650415..edc193c, tests: ./gradlew -p jmifx-gradle :codegen:test --console=plain → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.1/userguide/configuration_cache_enabling.html)
Task 5: complete (commits edc193c..8c32859, tests: ./gradlew -p jmifx-gradle :codegen:test --console=plain → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.1/userguide/configuration_cache_enabling.html)
Task 6: Ruling: IT needed test-side setAccessible on the generated private controller field — reflection ban applies to client runtime code, not tests. Cost if wrong: none (test-only).
Task 6: complete (commits 8c32859..7235bf7, tests: xvfb-run -a ./gradlew -p jmifx-gradle :codegen:test --console=plain → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.1/userguide/configuration_cache_enabling.html)
Task 7: Ruling: functional-test fixture uses an on-the-fly pure-Java stub jar (FxView with covariant Object getRoot + no-op FxViewRegistry) instead of the real framework — fixture cannot project()-depend across builds and includeBuild(root) would configure the whole demo app; real linkage proven by Task 10 demo. Cost if wrong: stub could drift from real API — mitigated by golden files + demo E2E.
Task 7: complete (commits 7235bf7..5816bc4, tests: ./gradlew -p jmifx-gradle :plugin:test --console=plain → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.1/userguide/configuration_cache_enabling.html)
Task 8: complete (commits 5816bc4..29cc9d4, tests: xvfb-run -a ./gradlew -p jmifx-gradle test --console=plain → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.1/userguide/configuration_cache_enabling.html)
Task 9: Ruling: (a) Boot 4 renamed artifacts — spring-boot-starter-web→spring-boot-starter-webmvc + spring-boot-webmvc-test, annotation moved to org.springframework.boot.webmvc.test.autoconfigure; (b) root forward asserted via getForwardedUrl() in MockMvc (no servlet container to re-dispatch) — real serving proven by servesIndexHtml + Task 11 E2E. Cost if wrong: test precision mismatch only.
Task 9: complete (commits 29cc9d4..a26b761, tests: ./gradlew :jmifx-starter:test --console=plain → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.1/userguide/configuration_cache_enabling.html)
Task 10: Ruling: (a) assets ride the client JAR (jar.from(pkg)) instead of a resources srcDir — teavm compiles from classes which includes processResources, so the srcDir path creates a task cycle; assets then land as a nested jar in BOOT-INF/lib (served fine by Boot classpath). Plan literal assertion BOOT-INF/classes/jmifx-web replaced by nested-jar check. (b) demo uses jmix { bomVersion = 3.0.3 } + artifact jmix-core-starter (erp pattern; plan had wrong artifact name). Cost if wrong: packaging path change only.
Task 10: complete (commits a26b761..70974a4, tests: ./gradlew :jmifx-demo:bootJar --console=plain → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.5.1/userguide/configuration_cache_enabling.html)
Task 11: Ruling: demo excludes dev.webfx from the client dependency — kit on server classpath breaks hibernate-validator (JavaFX value extractors); server needs only the asset jar. Cost if wrong: server boot crash.
Task 11: complete (commits 70974a4..018e1bf, tests: ls docs/superpowers/e2e/m1-browser.png docs/superpowers/e2e/m1-acceptance.md → docs/superpowers/e2e/m1-browser.png)

## Final review fixes (one pass, all RED→GREEN)

Final: fixed C1+C2 (emitter rewrite: stable names for every element incl. anonymous, full nesting support with per-container addAll, nested spacing; nested-anon golden + compile-IT) — emission/IT tests RED→GREEN, codegen suite 20/20.
Final: fixed I1 (duplicate generated-class detection 'duplicate view class X derived from files a and b') — rejectsDuplicateGeneratedClassNames RED→GREEN.
Final: fixed I3 (id → setId emitted; spacing on non-container → file:line error; element text content → file:line error) — 3 tests RED→GREEN.
Final: fixed I2 (kit deps moved implementation → compileOnly + teavmClasspath; demo dev.webfx exclusion REMOVED as no longer needed; app boots clean, /fx/ 200, browser renders) — RED was the witnessed Task-11 boot crash, GREEN is the post-fix boot.
Suite after fixes: codegen 20/20, plugin 5/5, jmifx 9/9, starter 5/5, root build + composite build green, wasm rebuilt, boot + browser verified.

## Final: minor (deferred)
- M1: missing-fx:controller short-circuit skips remaining errors in that file (single-error feedback on that path)
- M2: escape() now handles \n\r\t (fixed incidentally in the rewrite); remaining: other control chars (\u0000-\u001F) unescaped
- M3: default-package controller throws raw StringIndexOutOfBounds instead of a file:line error
- M4: rejectsDuplicateViewIdsAcrossFiles 'a'/'b' assertion is weak (matches any path chars)
- M5: FxViewRegistry.register not idempotent for identical factory (throws on any repeat)
- M6: dead code/unused imports (fxIdFields, anonCounter plumbing, File import, List import, FxTestKit Monocle comment, JmifxPluginExtension.getProject, eager task realize in JmifxPlugin)
- M7: snapshot-repo catch(Exception) logs at debug; no early validation of jmifx.mainClass
- M8: starter lacks ConditionalOnClass(WebMvcConfigurer)/SERVLET type; jmifx.path not normalized/validated
- M9: serves404WhenAssetsMissing tests missing file, not missing asset set (context-boots-empty proxied)
- M10: display-coupled tests need xvfb-run wrapper on headless boxes; no graceful skip
