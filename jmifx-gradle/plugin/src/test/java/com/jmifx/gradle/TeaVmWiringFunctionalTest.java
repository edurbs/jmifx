package com.jmifx.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TeaVmWiringFunctionalTest {

    @TempDir
    Path work;

    private Path fixture() throws IOException {
        Path source = Path.of("src/test/fixtures/teavm-project");
        try (Stream<Path> files = Files.walk(source)) {
            files.forEach(src -> {
                try {
                    Path dest = work.resolve(source.relativize(src).toString());
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(src, dest);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return work;
    }

    @Test
    void wasmTasksAndPackagingAreWired() throws IOException {
        Path project = fixture();

        var dry = org.gradle.testkit.runner.GradleRunner.create()
                .withProjectDir(project.toFile())
                .withArguments("packageJmifxWeb", "--dry-run", "--console=plain")
                .withPluginClasspath()
                .build();

        String out = dry.getOutput();
        assertTrue(out.contains(":generateWasmGC"), () -> out);
        assertTrue(out.contains(":copyWasmGCRuntime"), () -> out);
        assertTrue(out.contains(":packageJmifxWeb"), () -> out);
        assertTrue(out.contains(":compileJava"), () -> out);
        assertTrue(out.contains(":generateFxViews"), () -> out);
    }

    @Test
    void teavmMainClassComesFromJmifxExtension() throws IOException {
        Path project = fixture();

        var result = org.gradle.testkit.runner.GradleRunner.create()
                .withProjectDir(project.toFile())
                .withArguments("printTeavmMain", "--console=plain")
                .withPluginClasspath()
                .build();

        assertTrue(result.getOutput().contains("MAIN=client.DemoFxApp"), () -> result.getOutput());
    }

    @Test
    void assembleIncludesPackaging() throws IOException {
        Path project = fixture();

        var dry = org.gradle.testkit.runner.GradleRunner.create()
                .withProjectDir(project.toFile())
                .withArguments("assemble", "--dry-run", "--console=plain")
                .withPluginClasspath()
                .build();

        assertTrue(dry.getOutput().contains(":packageJmifxWeb"), () -> dry.getOutput());
    }
}
