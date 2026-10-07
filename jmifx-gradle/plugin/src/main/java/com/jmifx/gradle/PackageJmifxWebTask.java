package com.jmifx.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Packages the TeaVM wasm output plus a loader index.html into
 * {@code build/jmifx-web/jmifx-web/} — a dedicated directory registered as an
 * additional resources srcDir (never written into build/resources/main).
 */
public abstract class PackageJmifxWebTask extends DefaultTask {

    /** TeaVM wasm output dir (build/generated/teavm/wasm-gc). */
    @InputDirectory
    public abstract DirectoryProperty getWasmOutputDir();

    /** Package root (build/jmifx-web); assets land under jmifx-web/ inside it. */
    @OutputDirectory
    public abstract DirectoryProperty getWebDir();

    @Input
    public abstract Property<String> getProjectName();

    @TaskAction
    public void packageWeb() throws IOException {
        Path wasmDir = getWasmOutputDir().get().getAsFile().toPath();
        Path assetsDir = getWebDir().get().getAsFile().toPath().resolve("jmifx-web");
        Files.createDirectories(assetsDir);

        String wasmFile = null;
        String runtimeJs = null;
        try (Stream<Path> files = Files.list(wasmDir)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".wasm")) {
                    wasmFile = name;
                } else if (name.endsWith("-runtime.js")) {
                    runtimeJs = name;
                }
            }
        }
        if (wasmFile == null) {
            throw new GradleException("jmifx: no .wasm file found in " + wasmDir
                    + " — did the TeaVM build run?");
        }

        Files.copy(wasmDir.resolve(wasmFile), assetsDir.resolve(wasmFile),
                StandardCopyOption.REPLACE_EXISTING);
        if (runtimeJs != null) {
            Files.copy(wasmDir.resolve(runtimeJs), assetsDir.resolve(runtimeJs),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        String template = readTemplate();
        String html = template
                .replace("{{TITLE}}", getProjectName().get())
                .replace("{{WASM_FILE}}", wasmFile)
                .replace("{{RUNTIME_JS}}", runtimeJs != null ? runtimeJs : wasmFile + "-runtime.js");
        Files.writeString(assetsDir.resolve("index.html"), html);

        getLogger().lifecycle("jmifx: packaged wasm client into {}", assetsDir);
    }

    private String readTemplate() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/jmifx/index-template.html")) {
            if (in == null) {
                throw new GradleException("jmifx: index-template.html missing from plugin resources");
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
