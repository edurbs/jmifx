package com.jmifx.gradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GenerateFxViewsFunctionalTest {

    @TempDir
    Path work;

    /** Copies the fixture into a temp dir and injects the freshly built stub jar. */
    private Path fixture(String name) throws IOException {
        Path source = Path.of("src/test/fixtures/" + name);
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
                    throw new UncheckedIOExceptionWrapper(e);
                }
            });
        }
        return work;
    }

    @Test
    void generateFxViewsEmitsViewClassAndCompileSucceeds() throws Exception {
        Path project = fixture("hello-project");
        buildFrameworkStubJar(project.resolve("libs/jmifx-stub.jar"));
        Path generated = project.resolve("build/generated/fxviews/client/HelloView.java");

        var result = org.gradle.testkit.runner.GradleRunner.create()
                .withProjectDir(project.toFile())
                .withArguments("generateFxViews", "--console=plain")
                .withPluginClasspath()
                .build();

        assertEquals(org.gradle.testkit.runner.TaskOutcome.SUCCESS, result.task(":generateFxViews").getOutcome());
        assertTrue(Files.exists(generated), "expected " + generated);

        var compile = org.gradle.testkit.runner.GradleRunner.create()
                .withProjectDir(project.toFile())
                .withArguments("compileJava", "--console=plain")
                .withPluginClasspath()
                .build();

        assertEquals(org.gradle.testkit.runner.TaskOutcome.SUCCESS, compile.task(":compileJava").getOutcome(),
                () -> compile.getOutput());
    }

    @Test
    void invalidFxmlFailsTheTaskWithFileAndLine() throws IOException {
        Path project = fixture("error-project");

        var result = org.gradle.testkit.runner.GradleRunner.create()
                .withProjectDir(project.toFile())
                .withArguments("generateFxViews", "--console=plain")
                .withPluginClasspath()
                .buildAndFail();

        assertTrue(result.getOutput().contains(":3: unsupported element 'TableView'"),
                () -> result.getOutput());
    }

    /**
     * Builds a minimal pure-Java stand-in for the jmifx framework so the
     * fixture's compileJava works without a cross-build dependency. Covariant
     * returns keep it compatible with generated code: real getRoot() returns
     * Parent, stub returns Object.
     */
    private static void buildFrameworkStubJar(Path jar) throws Exception {
        Path src = jar.getParent().resolve("stub-src");
        Path out = jar.getParent().resolve("stub-out");
        Files.createDirectories(src.resolve("com/jmifx"));
        Files.createDirectories(out);
        Files.writeString(src.resolve("com/jmifx/FxView.java"), """
                package com.jmifx;
                public interface FxView {
                    String getId();
                    Object getRoot();
                }
                """);
        Files.writeString(src.resolve("com/jmifx/FxViewRegistry.java"), """
                package com.jmifx;
                import java.util.function.Supplier;
                public final class FxViewRegistry {
                    public void register(String id, Supplier<FxView> factory) {
                    }
                }
                """);
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, System.err,
                "-d", out.toString(), src.resolve("com/jmifx/FxView.java").toString(),
                src.resolve("com/jmifx/FxViewRegistry.java").toString()));
        Files.deleteIfExists(jar);
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("com/jmifx/FxView.class"));
            Files.copy(out.resolve("com/jmifx/FxView.class"), zos);
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("com/jmifx/FxViewRegistry.class"));
            Files.copy(out.resolve("com/jmifx/FxViewRegistry.class"), zos);
            zos.closeEntry();
        }
    }

    private static final class UncheckedIOExceptionWrapper extends RuntimeException {
        UncheckedIOExceptionWrapper(IOException cause) {
            super(cause);
        }
    }
}
