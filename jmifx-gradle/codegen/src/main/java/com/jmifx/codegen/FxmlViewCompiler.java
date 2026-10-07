package com.jmifx.codegen;

import com.jmifx.codegen.emit.ViewClassWriter;
import com.jmifx.codegen.emit.ViewsIndexWriter;
import com.jmifx.codegen.model.ElementNode;
import com.jmifx.codegen.model.ViewElement;
import com.jmifx.codegen.parser.FxmlParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses FXML files and validates them against the milestone-1 subset (spec §5).
 * Errors are cumulative: every problem in every file is reported with file:line.
 * Emission of Java sources happens only when there are no errors.
 */
public final class FxmlViewCompiler {

    /** Canonical order used in error messages. */
    public static final List<String> SUPPORTED_ELEMENTS = List.of(
            "VBox", "HBox", "StackPane", "Pane", "Label", "TextField", "Button");
    public static final Set<String> CONTAINERS = Set.of("VBox", "HBox", "StackPane", "Pane");
    public static final Set<String> CONTROLS = Set.of("Label", "TextField", "Button");
    public static final List<String> SUPPORTED_ATTRIBUTES = List.of(
            "fx:id", "id", "text", "promptText", "prefWidth", "prefHeight", "maxWidth",
            "spacing", "alignment", "onAction");

    /** All javafx.geometry.Pos enum constant names (codegen must stay JavaFX-free). */
    private static final Set<String> POS_NAMES = Set.of(
            "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
            "CENTER_LEFT", "CENTER", "CENTER_RIGHT",
            "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT",
            "BASELINE_LEFT", "BASELINE_CENTER", "BASELINE_RIGHT");

    private final FxmlParser parser = new FxmlParser();

    public CompilationResult compile(List<Path> fxmlFiles, Path generatedSourcesDir) {
        List<FxmlCompileError> errors = new ArrayList<>();
        List<ViewElement> views = new ArrayList<>();
        Map<String, Path> viewIdToFirstFile = new HashMap<>();
        Map<String, Path> classNameToFirstFile = new HashMap<>();

        for (Path fxmlFile : fxmlFiles) {
            ElementNode root = parser.parse(fxmlFile);
            String viewId = viewId(fxmlFile);
            String controller = root.attribute("fx:controller");

            if (controller == null) {
                errors.add(new FxmlCompileError(fxmlFile, root.line(), "fx:controller is required"));
                continue; // cannot emit without a controller
            }

            if (!CONTAINERS.contains(root.tag())) {
                errors.add(new FxmlCompileError(fxmlFile, root.line(),
                        "root element must be one of VBox, HBox, StackPane, Pane"));
            }

            validateTree(fxmlFile, root, errors);

            ViewElement view = new ViewElement(viewId, controller, root, fxmlFile);

            Path firstFile = viewIdToFirstFile.putIfAbsent(viewId, fxmlFile);
            if (firstFile != null) {
                errors.add(new FxmlCompileError(fxmlFile, 1,
                        "duplicate view id '" + viewId + "' in files " + firstFile + " and " + fxmlFile));
            }

            // login.fxml and login-view.fxml both derive LoginView — same
            // package would silently overwrite the first generated class
            String classKey = packageOf(controller) + "." + view.generatedClassName();
            Path firstClassFile = classNameToFirstFile.putIfAbsent(classKey, fxmlFile);
            if (firstClassFile != null) {
                errors.add(new FxmlCompileError(fxmlFile, 1,
                        "duplicate view class '" + view.generatedClassName()
                                + "' derived from files " + firstClassFile + " and " + fxmlFile));
            }

            views.add(view);
        }

        CompilationResult result = new CompilationResult(errors, views);
        if (result.success() && !views.isEmpty()) {
            emit(result.views(), generatedSourcesDir);
        }
        return result;
    }

    private void emit(List<ViewElement> views, Path generatedSourcesDir) {
        ViewClassWriter viewWriter = new ViewClassWriter();
        for (ViewElement view : views) {
            String pkg = view.controllerFqcn();
            int dot = pkg.lastIndexOf('.');
            String pkgPath = dot < 0 ? "" : pkg.substring(0, dot).replace('.', '/');
            Path file = generatedSourcesDir.resolve(pkgPath)
                    .resolve(view.generatedClassName() + ".java");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, viewWriter.render(view));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write " + file, e);
            }
        }
        Path index = generatedSourcesDir.resolve("com/jmifx/generated/FxViewsIndex.java");
        try {
            Files.createDirectories(index.getParent());
            Files.writeString(index, new ViewsIndexWriter().render(views));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + index, e);
        }
    }

    private void validateTree(Path file, ElementNode root, List<FxmlCompileError> errors) {
        Set<String> seenFxIds = new HashSet<>();
        root.walk(element -> {
            if (!SUPPORTED_ELEMENTS.contains(element.tag())) {
                errors.add(new FxmlCompileError(file, element.line(),
                        "unsupported element '" + element.tag() + "' (supported: "
                                + String.join(", ", SUPPORTED_ELEMENTS) + ")"));
            }
            if (element.textContent() != null) {
                errors.add(new FxmlCompileError(file, element.textLine(),
                        "element text content is not supported (use the text attribute)"));
            }
            for (Map.Entry<String, String> attr : element.attributes().entrySet()) {
                if (attr.getKey().equals("fx:controller")) {
                    continue; // consumed as the controller declaration, not a node property
                }
                validateAttribute(file, element, attr.getKey(), attr.getValue(), errors);
            }
            String fxId = element.attribute("fx:id");
            if (fxId != null && !seenFxIds.add(fxId)) {
                errors.add(new FxmlCompileError(file, element.line(), "duplicate fx:id '" + fxId + "'"));
            }
        });
    }

    private void validateAttribute(Path file, ElementNode element, String name, String value,
                                   List<FxmlCompileError> errors) {
        if (!SUPPORTED_ATTRIBUTES.contains(name)) {
            errors.add(new FxmlCompileError(file, element.line(),
                    "unsupported attribute '" + name + "' on '" + element.tag() + "' (supported: "
                            + String.join(", ", SUPPORTED_ATTRIBUTES) + ")"));
            return;
        }
        if ("onAction".equals(name) && !"Button".equals(element.tag())) {
            errors.add(new FxmlCompileError(file, element.line(),
                    "onAction is only supported on Button"));
            return;
        }
        if ("spacing".equals(name) && !"VBox".equals(element.tag()) && !"HBox".equals(element.tag())) {
            errors.add(new FxmlCompileError(file, element.line(),
                    "spacing is only supported on VBox and HBox"));
            return;
        }
        switch (name) {
            case "prefWidth", "prefHeight", "maxWidth", "spacing" -> {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    invalidValue(file, element, name, value, errors);
                }
            }
            case "alignment" -> {
                if (!POS_NAMES.contains(value)) {
                    invalidValue(file, element, name, value, errors);
                }
            }
            default -> { /* no value validation */ }
        }
    }

    private void invalidValue(Path file, ElementNode element, String name, String value,
                              List<FxmlCompileError> errors) {
        errors.add(new FxmlCompileError(file, element.line(),
                "invalid value '" + value + "' for attribute '" + name + "'"));
    }

    private static String viewId(Path fxmlFile) {
        String name = fxmlFile.getFileName().toString();
        return name.substring(0, name.lastIndexOf('.'));
    }

    private static String packageOf(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? "" : fqcn.substring(0, dot);
    }
}
