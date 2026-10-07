package com.jmifx.codegen.emit;

import com.jmifx.codegen.model.ElementNode;
import com.jmifx.codegen.model.ViewElement;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Emits one reflection-free view class per FXML file. Every element — at any
 * nesting depth, with or without fx:id — becomes a field with a stable name
 * (fx:id when present, else {@code tag_n} numbered in document order). The
 * emission shape is pinned by the golden files in src/test/resources/golden —
 * change the goldens and the emission tests together.
 */
public final class ViewClassWriter {

    /** Renders the Java source for one view (no file I/O — testable as pure text). */
    public String render(ViewElement view) {
        ElementNode root = view.root();
        String controller = view.controllerFqcn();
        String controllerPackage = packageOf(controller);
        String className = view.generatedClassName();

        // Pre-pass: one stable variable name per element (document order)
        Map<ElementNode, String> names = new IdentityHashMap<>();
        List<ElementNode> documentOrder = new ArrayList<>();
        assignNames(root, names, documentOrder, new int[1]);

        List<String> fields = new ArrayList<>();
        List<String> body = new ArrayList<>();

        // Fields: controller first, then every element in document order
        fields.add("    private final " + controller + " controller = new " + controller + "();");
        boolean first = true;
        for (ElementNode element : documentOrder) {
            String spacing = element.attribute("spacing");
            String ctor = (first && spacing != null)
                    ? "new " + element.tag() + "(" + Double.parseDouble(spacing) + ")"
                    : "new " + element.tag() + "()";
            fields.add("    private final " + element.tag() + " " + names.get(element) + " = " + ctor + ";");
            first = false;
        }

        // Property statements in document order (root spacing consumed by ctor)
        for (ElementNode element : documentOrder) {
            String var = names.get(element);
            boolean isRoot = element == root;
            for (Map.Entry<String, String> attr : element.attributes().entrySet()) {
                appendProperty(element, var, attr.getKey(), attr.getValue(), isRoot, body);
            }
        }

        // Child wiring per container, outermost first
        for (ElementNode element : documentOrder) {
            if (!element.children().isEmpty()) {
                List<String> childVars = new ArrayList<>();
                for (ElementNode child : element.children()) {
                    childVars.add(names.get(child));
                }
                body.add("        " + names.get(element) + ".getChildren().addAll("
                        + String.join(", ", childVars) + ");");
            }
        }

        // Controller field injection (document order, fx:id elements only)
        for (ElementNode element : documentOrder) {
            String fxId = element.attribute("fx:id");
            if (fxId != null) {
                body.add("        controller." + fxId + " = " + names.get(element) + ";");
            }
        }
        body.add("        controller.initialize();");

        // Imports based on what the view actually uses
        TreeSet<String> controlImports = new TreeSet<>();
        TreeSet<String> layoutImports = new TreeSet<>();
        boolean usesActionEvent = false;
        boolean usesPos = false;
        for (ElementNode element : documentOrder) {
            switch (element.tag()) {
                case "Label" -> controlImports.add("import javafx.scene.control.Label;\n");
                case "TextField" -> controlImports.add("import javafx.scene.control.TextField;\n");
                case "Button" -> controlImports.add("import javafx.scene.control.Button;\n");
                case "VBox" -> layoutImports.add("import javafx.scene.layout.VBox;\n");
                case "HBox" -> layoutImports.add("import javafx.scene.layout.HBox;\n");
                case "StackPane" -> layoutImports.add("import javafx.scene.layout.StackPane;\n");
                case "Pane" -> layoutImports.add("import javafx.scene.layout.Pane;\n");
                default -> { }
            }
            if (element.attribute("onAction") != null) {
                usesActionEvent = true;
            }
            if (element.attribute("alignment") != null) {
                usesPos = true;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from ").append(view.sourceFile().getFileName())
                .append(" by jmifx-codegen — DO NOT EDIT\n");
        if (!controllerPackage.isEmpty()) {
            sb.append("package ").append(controllerPackage).append(";\n\n");
        }
        sb.append("import com.jmifx.FxView;\n");
        if (usesActionEvent) {
            sb.append("import javafx.event.ActionEvent;\n");
        }
        if (usesPos) {
            sb.append("import javafx.geometry.Pos;\n");
        }
        sb.append("import javafx.scene.Parent;\n");
        controlImports.forEach(sb::append);
        layoutImports.forEach(sb::append);
        sb.append('\n');
        sb.append("public class ").append(className).append(" implements FxView {\n\n");
        for (String field : fields) {
            sb.append(field).append('\n');
        }
        sb.append('\n');
        sb.append("    public ").append(className).append("() {\n");
        for (String statement : body) {
            sb.append(statement).append('\n');
        }
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public String getId() {\n");
        sb.append("        return \"").append(view.viewId()).append("\";\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public Parent getRoot() {\n");
        sb.append("        return root;\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    // --- helpers -------------------------------------------------------------

    private void assignNames(ElementNode element, Map<ElementNode, String> names, List<ElementNode> order,
                             int[] anonCounter) {
        order.add(element);
        String fxId = element.attribute("fx:id");
        if (fxId != null) {
            names.put(element, fxId);
        } else if (order.size() == 1) {
            names.put(element, "root"); // the root element is always addressable as `root`
        } else {
            names.put(element, element.tag().toLowerCase() + "_" + (++anonCounter[0]));
        }
        for (ElementNode child : element.children()) {
            assignNames(child, names, order, anonCounter);
        }
    }

    private void appendProperty(ElementNode element, String var, String name, String value,
                                boolean isRoot, List<String> body) {
        switch (name) {
            case "text" -> body.add("        " + var + ".setText(\"" + escape(value) + "\");");
            case "promptText" -> body.add("        " + var + ".setPromptText(\"" + escape(value) + "\");");
            case "id" -> body.add("        " + var + ".setId(\"" + escape(value) + "\");");
            case "prefWidth" -> body.add("        " + var + ".setPrefWidth(" + Double.parseDouble(value) + ");");
            case "prefHeight" -> body.add("        " + var + ".setPrefHeight(" + Double.parseDouble(value) + ");");
            case "maxWidth" -> body.add("        " + var + ".setMaxWidth(" + Double.parseDouble(value) + ");");
            case "spacing" -> {
                if (!isRoot) {
                    body.add("        " + var + ".setSpacing(" + Double.parseDouble(value) + ");");
                } // root spacing consumed by the constructor
            }
            case "alignment" -> body.add("        " + var + ".setAlignment(Pos.valueOf(\"" + value + "\"));");
            case "onAction" -> body.add("        " + var + ".addEventHandler(ActionEvent.ACTION, controller::"
                    + value.replaceFirst("^#", "") + ");");
            default -> { /* fx:id / fx:controller consumed elsewhere */ }
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String packageOf(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? "" : fqcn.substring(0, dot);
    }
}
