package com.jmifx.gradle;

import org.gradle.api.Project;

/** Configuration surface for the {@code jmifx} plugin extension. */
public abstract class JmifxPluginExtension {

    private final Project project;

    public JmifxPluginExtension(Project project) {
        this.project = project;
        getFxmlSourceDir().convention(project.getLayout().getProjectDirectory().dir("src/main/fxml"));
        getGeneratedDir().convention(project.getLayout().getBuildDirectory().dir("generated/fxviews"));
    }

    /** Directory containing {@code *.fxml} view descriptors. Default: src/main/fxml */
    public abstract org.gradle.api.file.DirectoryProperty getFxmlSourceDir();

    /** Output directory for generated Java sources. Default: build/generated/fxviews */
    public abstract org.gradle.api.file.DirectoryProperty getGeneratedDir();

    /** TeaVM main class (the app's FxApplication subclass) — required by the wasm wiring. */
    public abstract org.gradle.api.provider.Property<String> getMainClass();

    public Project getProject() {
        return project;
    }
}
