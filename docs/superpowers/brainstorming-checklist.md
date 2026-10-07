# Brainstorming Checklist — jmifx (Jmix + JavaFX/WebFX add-on)

Classification: **Architectural** (new project)

## Checklist

- [x] 1. Explore project context (jmifx empty; jmix framework source at ../jmix; erp app on Jmix 3.0.3; WebFX/TeaVM research)
- [x] 2. Visual companion — not needed (no visual questions arose)
- [x] 3. Clarifying questions — full-replacement topology; pipeline PoC scope; Jmix 3.0.3
- [x] 4. Approaches proposed — A codegen-first chosen (B runtime loader, C CLI bridge rejected)
- [x] 5. Design presented in 4 sections — all approved
- [x] 6. Design doc written to docs/superpowers/specs/2026-10-06-jmifx-design.md + committed (git init, commit 8d53140)
- [x] 7. Spec self-review — fixed jmifg typo, FxView interface ambiguity, removed unused init() hook
- [x] 8. User reviews written spec — approved ("start writing out the implementation plan")
- [x] 9. writing-plans invoked — plan at docs/superpowers/plans/2026-10-06-jmifx-milestone1.md (11 tasks, spike-first)

## Key research findings

- WebFX officially supports TeaVM → WebAssembly GC (Nov 2025). Modern browsers only.
- WebFX Kit supported controls include **Button, Label, TextField** (also CheckBox, RadioButton, Hyperlink, TextArea, PasswordField, ProgressBar, Slider, ScrollPane, SplitPane, TabPane).
- **FXML is NOT supported at runtime** by WebFX (reflection-based FXMLLoader incompatible with transpilers). Official recommendation: *transform FXML files into transpilable Java code* — proven by WebFX Memory Game prototype.
- WebFX is Maven-first (WebFX CLI generates build chain from webfx.xml, invokes TeaVM/GWT Maven plugins). TeaVM itself has a first-class **Gradle plugin** (org.teavm 0.15+, wasmGC target).
- WebFX Stack has UI router, i18n, auth, client ORM (Vert.x-focused server side; Spring Boot impl "might be considered in the future").
- Jmix add-on structure (from jmix repo): multi-module, `<name>` + `<name>-starter` (+ `-flowui`, `-flowui-starter` when UI involved).
- erp app precedent: builds an npm SPA and packages it into the Spring Boot jar as static resources — same packaging pattern works for a WebFX/Wasm client.
- erp runs Jmix **3.0.3** (Gradle).
- WebFX platform has an **XML AST plugin** (webfx-platform-ast-xml-plugin) → XML parsing is possible cross-platform (relevant to a reflection-free FXML loader alternative).
