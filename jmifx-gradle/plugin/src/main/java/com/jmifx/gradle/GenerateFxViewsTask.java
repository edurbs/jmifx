package com.jmifx.gradle;

import com.jmifx.codegen.CompilationResult;
import com.jmifx.codegen.FxmlCompileError;
import com.jmifx.codegen.FxmlViewCompiler;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs the FXML→Java compiler over every {@code *.fxml} in the source dir and
 * writes generated sources to the output dir. Validation errors fail the build
 * with one line per error: {@code <file>:<line>: <message>}.
 */
public abstract class GenerateFxViewsTask extends DefaultTask {

    @InputDirectory
    @Optional
    public abstract DirectoryProperty getFxmlSourceDir();

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedDir();

    @TaskAction
    public void generate() throws IOException {
        Path sourceDir = getFxmlSourceDir().isPresent()
                ? getFxmlSourceDir().get().getAsFile().toPath() : null;
        Path outputDir = getGeneratedDir().get().getAsFile().toPath();

        if (sourceDir == null || !Files.isDirectory(sourceDir)) {
            getProject().getLogger().lifecycle("jmifx: no FXML source directory at {} — nothing to generate",
                    sourceDir);
            return;
        }

        List<Path> fxmlFiles;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            fxmlFiles = walk.filter(p -> p.toString().endsWith(".fxml")).sorted().toList();
        }
        if (fxmlFiles.isEmpty()) {
            getProject().getLogger().lifecycle("jmifx: no .fxml files under {}", sourceDir);
            return;
        }

        cleanPreviousOutput(outputDir);

        CompilationResult result = new FxmlViewCompiler().compile(fxmlFiles, outputDir);
        if (!result.success()) {
            StringBuilder message = new StringBuilder("jmifx: FXML compilation failed:\n");
            for (FxmlCompileError error : result.errors()) {
                message.append("  ").append(error.format()).append('\n');
            }
            throw new GradleException(message.toString());
        }
        for (var view : result.views()) {
            getProject().getLogger().lifecycle("jmifx: generated {} for view '{}'",
                    view.generatedClassName(), view.viewId());
        }
    }

    private void cleanPreviousOutput(Path outputDir) throws IOException {
        if (Files.isDirectory(outputDir)) {
            try (Stream<Path> walk = Files.walk(outputDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .filter(p -> !p.equals(outputDir))
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new UncheckedIOExceptionWrapper(e);
                            }
                        });
            }
        }
        Files.createDirectories(outputDir);
    }

    private static final class UncheckedIOExceptionWrapper extends RuntimeException {
        UncheckedIOExceptionWrapper(IOException cause) {
            super(cause);
        }
    }
}
