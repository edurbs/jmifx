package com.jmifx.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmissionTest {

    @TempDir
    Path generatedDir;

    private Path fixture(String name) {
        return Path.of(getClass().getResource("/fxml/" + name).getPath());
    }

    private List<String> normalized(Path file) throws Exception {
        return Files.readAllLines(file).stream()
                .map(String::stripTrailing)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private List<String> golden(String name) throws Exception {
        return normalized(Path.of(getClass().getResource("/golden/" + name).getPath()));
    }

    @Test
    void emitsViewClassMatchingGoldenFile() throws Exception {
        new FxmlViewCompiler().compile(List.of(fixture("hello-view.fxml")), generatedDir);

        Path emitted = generatedDir.resolve("fixture/HelloView.java");
        assertTrue(Files.exists(emitted), "expected " + emitted);
        assertEquals(golden("HelloView.java.txt"), normalized(emitted));
    }

    @Test
    void emitsIndexMatchingGoldenFile() throws Exception {
        new FxmlViewCompiler().compile(List.of(fixture("hello-view.fxml")), generatedDir);

        Path emitted = generatedDir.resolve("com/jmifx/generated/FxViewsIndex.java");
        assertTrue(Files.exists(emitted), "expected " + emitted);
        assertEquals(golden("FxViewsIndex.java.txt"), normalized(emitted));
    }

    @Test
    void nothingIsEmittedWhenThereAreErrors() throws Exception {
        new FxmlViewCompiler().compile(List.of(fixture("unsupported-element.fxml")), generatedDir);

        try (var files = Files.walk(generatedDir)) {
            assertEquals(0, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void nestedContainersAndAnonymousChildrenEmitCorrectCode() throws Exception {
        new FxmlViewCompiler().compile(List.of(fixture("nested-anon.fxml")), generatedDir);

        Path emitted = generatedDir.resolve("fixture/NestedAnonView.java");
        assertTrue(Files.exists(emitted), "expected " + emitted);
        assertEquals(golden("NestedAnonView.java.txt"), normalized(emitted));
    }

    @Test
    void idAttributeEmitsSetId() throws Exception {
        new FxmlViewCompiler().compile(List.of(fixture("nested-anon.fxml")), generatedDir);

        String source = Files.readString(generatedDir.resolve("fixture/NestedAnonView.java"));
        assertTrue(source.contains("bLabel.setId(\"b-label\");"), source);
    }
}
