package com.jmifx.codegen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compiles the GENERATED sources with a real javac and instantiates the result:
 * proves the emitted Java is valid, wires the controller correctly and builds
 * the expected scene graph. Runs under xvfb (JavaFX controls class-init).
 */
class CodegenCompileIT {

    @TempDir
    Path work;

    @BeforeAll
    static void initToolkit() {
        try {
            javafx.application.Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // fine
        }
    }

    @Test
    void generatedViewCompilesInstantiatesAndWiresController() throws Exception {
        // 1. Generate sources from the hello-view fixture
        Path fxml = Path.of(getClass().getResource("/fxml/hello-view.fxml").getPath());
        Path generated = work.resolve("generated");
        CompilationResult result = new FxmlViewCompiler().compile(List.of(fxml), generated);
        assertTrue(result.success(), () -> result.errors().toString());

        // 2. Copy the fixture controller next to the generated view class
        String controllerSrc = Files.readString(
                Path.of(getClass().getResource("/javafixture/HelloController.java.txt").getPath()));
        Files.createDirectories(generated.resolve("fixture"));
        Files.writeString(generated.resolve("fixture/HelloController.java"), controllerSrc);

        // 3. Compile: generated sources + controller, classpath = this test's
        //    classpath (FxView/FxViewRegistry stubs + javafx-controls)
        Path out = work.resolve("out");
        Files.createDirectories(out);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> options = List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", out.toString());
        List<String> files = List.of(
                generated.resolve("fixture/HelloView.java").toString(),
                generated.resolve("fixture/HelloController.java").toString(),
                generated.resolve("com/jmifx/generated/FxViewsIndex.java").toString());
        int exit = compiler.run(null, null, System.err,
                join(options, files));
        assertEquals(0, exit, "javac failed on generated sources — see stderr above");

        // 4. Load and instantiate
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{ out.toUri().toURL() },
                getClass().getClassLoader())) {
            Class<?> viewClass = Class.forName("fixture.HelloView", true, loader);
            var view = viewClass.getDeclaredConstructor().newInstance();

            assertEquals("hello-view", viewClass.getMethod("getId").invoke(view));

            var root = (javafx.scene.Parent) viewClass.getMethod("getRoot").invoke(view);
            assertInstanceOf(javafx.scene.layout.VBox.class, root);
            var children = ((javafx.scene.layout.VBox) root).getChildren();
            assertEquals(3, children.size());
            assertInstanceOf(javafx.scene.control.Label.class, children.get(0));
            assertInstanceOf(javafx.scene.control.TextField.class, children.get(1));
            assertInstanceOf(javafx.scene.control.Button.class, children.get(2));

            var button = (javafx.scene.control.Button) children.get(2);
            assertEquals("Greet", button.getText());

            // 5. Handler wiring: firing the button reaches the controller
            var controllerField = viewClass.getDeclaredField("controller");
            controllerField.setAccessible(true);
            Object controller = controllerField.get(view);
            @SuppressWarnings("unchecked")
            List<String> events = (List<String>) controller.getClass().getField("events").get(controller);
            assertTrue(events.containsAll(List.of("init:greetingLabel", "init:nameField", "init:greetButton")),
                    () -> "controller injections missing: " + events);
            int before = events.size();
            button.fire();
            assertTrue(events.size() == before + 1 && events.get(before).equals("greet"),
                    () -> "handler not wired: " + events);
        }
    }

    private static String[] join(List<String> options, List<String> files) {
        String[] all = new String[options.size() + files.size()];
        for (int i = 0; i < options.size(); i++) {
            all[i] = options.get(i);
        }
        for (int i = 0; i < files.size(); i++) {
            all[options.size() + i] = files.get(i);
        }
        return all;
    }
}
