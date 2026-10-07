package com.jmifx.gradle;

import org.gradle.api.Project;
import org.gradle.api.Plugin;

/**
 * Applied to client modules of a Jmix application that contain FXML views.
 * Wires FXML→Java code generation into the compile pipeline; Task 8 adds the
 * TeaVM/Wasm wiring on top.
 */
public final class JmifxPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        JmifxPluginExtension extension =
                project.getExtensions().create("jmifx", JmifxPluginExtension.class, project);

        GenerateFxViewsTask generate = project.getTasks()
                .register("generateFxViews", GenerateFxViewsTask.class, task -> {
                    task.getFxmlSourceDir().set(extension.getFxmlSourceDir());
                    task.getGeneratedDir().set(extension.getGeneratedDir());
                }).get();

        // Client modules compile with a TeaVM-compatible release level (spec §7)
        project.getTasks().withType(org.gradle.api.tasks.compile.JavaCompile.class).configureEach(
                compile -> compile.getOptions().getRelease().set(21));

        var sourceSets = project.getExtensions()
                .getByType(org.gradle.api.plugins.JavaPluginExtension.class).getSourceSets();
        var main = sourceSets.getByName("main");
        main.getJava().srcDir(generate.getGeneratedDir());
        project.getTasks().named("compileJava",
                task -> task.dependsOn(generate));
    }
}
