package com.jmifx.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.teavm.gradle.api.TeaVMExtension;

import java.util.List;

/**
 * Applies and configures the TeaVM plugin for wasmGC compilation, adds the
 * WebFX Kit dependency set validated by the spike
 * (docs/superpowers/spikes/2026-10-06-webfx-teavm-gradle.md) and registers
 * the web packaging task.
 */
public final class TeaVmWiring {

    /** Pinned: matches webfx-parent's teavm.version (spec risk #3). */
    public static final String TEAVM_PLUGIN_VERSION = "0.14.1";
    public static final String WEBFX_VERSION = "0.1.0-SNAPSHOT";
    public static final String WEBFX_SNAPSHOT_REPO = "https://central.sonatype.com/repository/maven-snapshots/";

    /** Exact artifact set validated by the spike — do not edit without re-running it. */
    static final List<String> WEBFX_ARTIFACTS = List.of(
            "webfx-kit-javafxgraphics-elemental2",
            "webfx-kit-javafxgraphics-registry-elemental2",
            "webfx-kit-javafxcontrols-emul",
            "webfx-kit-javafxcontrols-registry-elemental2",
            "webfx-platform-teavm-elemental2-polyfill",
            "webfx-platform-boot-java",
            "webfx-platform-console-elemental2",
            "webfx-platform-os-elemental2",
            "webfx-platform-resource-teavm",
            "webfx-platform-resource-web",
            "webfx-platform-shutdown-elemental2",
            "webfx-platform-storage-elemental2",
            "webfx-platform-uischeduler-elemental2",
            "webfx-platform-useragent-elemental2");

    private TeaVmWiring() {
    }

    static void wire(Project project, JmifxPluginExtension extension) {
        project.getPluginManager().apply("org.teavm");

        // Snapshot repo for the WebFX Kit (harmless if the build already declares it)
        project.getRepositories().maven(repo ->
                repo.setUrl(WEBFX_SNAPSHOT_REPO));

        DependencyHandler deps = project.getDependencies();
        for (String artifact : WEBFX_ARTIFACTS) {
            deps.add("implementation", "dev.webfx:" + artifact + ":" + WEBFX_VERSION);
        }

        TeaVMExtension teavm = (TeaVMExtension) project.getExtensions().getByName("teavm");
        teavm.getAll().getMainClass().set(extension.getMainClass());
        teavm.getWasmGC().getAddedToWebApp().set(true);
        teavm.getWasmGC().getTargetFileName().set("app.wasm");

        var pkg = project.getTasks().register("packageJmifxWeb", PackageJmifxWebTask.class, task -> {
            task.getWasmOutputDir().set(
                    project.getLayout().getBuildDirectory().dir("generated/teavm/wasm-gc"));
            task.getWebDir().set(project.getLayout().getBuildDirectory().dir("jmifx-web"));
            task.getProjectName().set(project.getName());
        });
        pkg.configure(task -> task.dependsOn(project.getTasks().named("buildWasmGC")));
        project.getTasks().named("assemble", task -> task.dependsOn(pkg));

        // Dedicated resources dir for packaged web assets (never build/resources/main)
        var sourceSets = project.getExtensions()
                .getByType(org.gradle.api.plugins.JavaPluginExtension.class).getSourceSets();
        sourceSets.getByName("main").getResources()
                .srcDir(project.getLayout().getBuildDirectory().dir("jmifx-web"));
    }
}
