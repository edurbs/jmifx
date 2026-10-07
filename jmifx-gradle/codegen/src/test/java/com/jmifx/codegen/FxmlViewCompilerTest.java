package com.jmifx.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FxmlViewCompilerTest {

    @TempDir
    Path generatedDir;

    private Path fixture(String name) {
        return Path.of(getClass().getResource("/fxml/" + name).getPath());
    }

    private Path dupFixture(String sub) {
        return Path.of(getClass().getResource("/fxml/dupdir/" + sub + "/hello-view.fxml").getPath());
    }

    @Test
    void validHelloViewParsesWithoutErrors() {
        FxmlViewCompiler compiler = new FxmlViewCompiler();

        CompilationResult result = compiler.compile(List.of(fixture("hello-view.fxml")), generatedDir);

        assertTrue(result.errors().isEmpty(), () -> result.errors().toString());
        assertTrue(result.success());
        assertEquals(1, result.views().size());
        assertEquals("hello-view", result.views().get(0).viewId());
        assertEquals("fixture.HelloController", result.views().get(0).controllerFqcn());
        assertEquals("VBox", result.views().get(0).root().tag());
        assertEquals(3, result.views().get(0).root().children().size());
    }

    @Test
    void rejectsUnsupportedElement() {
        FxmlViewCompiler compiler = new FxmlViewCompiler();

        CompilationResult result = compiler.compile(List.of(fixture("unsupported-element.fxml")), generatedDir);

        assertFalse(result.success());
        FxmlCompileError error = result.errors().get(0);
        assertTrue(error.format().contains("unsupported-element.fxml:3: unsupported element 'TableView' "
                + "(supported: VBox, HBox, StackPane, Pane, Label, TextField, Button)"),
                () -> error.format());
    }

    @Test
    void rejectsUnsupportedAttribute() {
        FxmlViewCompiler compiler = new FxmlViewCompiler();

        CompilationResult result = compiler.compile(List.of(fixture("unsupported-attribute.fxml")), generatedDir);

        assertFalse(result.success());
        FxmlCompileError error = result.errors().get(0);
        assertTrue(error.format().contains("unsupported-attribute.fxml:3: "
                        + "unsupported attribute 'style' on 'Button' "
                        + "(supported: fx:id, id, text, promptText, prefWidth, prefHeight, maxWidth, spacing, alignment, onAction)"),
                () -> error.format());
    }

    @Test
    void rejectsMissingController() {
        FxmlViewCompiler compiler = new FxmlViewCompiler();

        CompilationResult result = compiler.compile(List.of(fixture("missing-controller.fxml")), generatedDir);

        assertFalse(result.success());
        FxmlCompileError error = result.errors().get(0);
        assertTrue(error.format().contains("missing-controller.fxml:2: fx:controller is required"),
                () -> error.format());
    }

    @Test
    void rejectsDuplicateFxIdWithinFile() {
        FxmlViewCompiler compiler = new FxmlViewCompiler();

        CompilationResult result = compiler.compile(List.of(fixture("duplicate-id-inner.fxml")), generatedDir);

        assertFalse(result.success());
        FxmlCompileError error = result.errors().get(0);
        assertTrue(error.format().contains("duplicate fx:id 'dup'"), () -> error.format());
    }

    @Test
    void rejectsDuplicateViewIdsAcrossFiles() {
        FxmlViewCompiler compiler = new FxmlViewCompiler();

        CompilationResult result = compiler.compile(
                List.of(dupFixture("a"), dupFixture("b")), generatedDir);

        assertFalse(result.success());
        FxmlCompileError error = result.errors().get(0);
        String formatted = error.format();
        assertTrue(formatted.contains("duplicate view id 'hello-view' in files "), () -> formatted);
        assertTrue(formatted.contains("a") && formatted.contains("b"), () -> formatted);
    }

    @Test
    void rejectsOnActionOnNonButton() throws IOException {
        Path fxml = Files.writeString(generatedDir.resolve("bad.fxml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <VBox xmlns:fx="http://javafx.com/fxml" fx:controller="fixture.Bad">
                    <TextField onAction="#handle"/>
                </VBox>
                """);

        FxmlViewCompiler compiler = new FxmlViewCompiler();

        CompilationResult result = compiler.compile(List.of(fxml), generatedDir);

        FxmlCompileError error = result.errors().get(0);
        assertTrue(error.format().contains("bad.fxml:3: onAction is only supported on Button"),
                () -> error.format());
    }

    @Test
    void rejectsInvalidNumericAndAlignmentValues() throws IOException {
        Path fxml = Files.writeString(generatedDir.resolve("badvals.fxml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <VBox xmlns:fx="http://javafx.com/fxml" fx:controller="fixture.Bad" prefWidth="wide" alignment="MIDDLE">
                    <Label text="x"/>
                </VBox>
                """);

        FxmlViewCompiler compiler = new FxmlViewCompiler();

        CompilationResult result = compiler.compile(List.of(fxml), generatedDir);

        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream().anyMatch(e ->
                e.format().contains("invalid value 'wide' for attribute 'prefWidth'")));
        assertTrue(result.errors().stream().anyMatch(e ->
                e.format().contains("invalid value 'MIDDLE' for attribute 'alignment'")));
    }

    @Test
    void rejectsNonContainerRootElement() throws IOException {
        Path fxml = Files.writeString(generatedDir.resolve("badroot.fxml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Label xmlns:fx="http://javafx.com/fxml" fx:controller="fixture.Bad" text="root"/>
                """);

        FxmlViewCompiler compiler = new FxmlViewCompiler();

        CompilationResult result = compiler.compile(List.of(fxml), generatedDir);

        FxmlCompileError error = result.errors().get(0);
        assertTrue(error.format().contains("badroot.fxml:2: root element must be one of VBox, HBox, StackPane, Pane"),
                () -> error.format());
    }

    @Test
    void errorsAreCumulativeNotFirstOnly() throws IOException {
        Path fxml = Files.writeString(generatedDir.resolve("many.fxml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <StackPane xmlns:fx="http://javafx.com/fxml" fx:controller="fixture.Bad">
                    <TableView/>
                    <ComboBox/>
                </StackPane>
                """);

        FxmlViewCompiler compiler = new FxmlViewCompiler();

        CompilationResult result = compiler.compile(List.of(fxml), generatedDir);

        assertEquals(2, result.errors().size(), () -> result.errors().toString());
    }
}
