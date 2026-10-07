package com.jmifx.codegen.emit;

import com.jmifx.codegen.model.ElementNode;
import com.jmifx.codegen.model.ViewElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Emits one reflection-free view class per FXML file. The shape is pinned by
 * the golden files in src/test/resources/golden — change the goldens and these
 * tests together.
 */
public final class ViewClassWriter {

    /** Renders the Java source for one view (no file I/O — testable as pure text). */
    public String render(ViewElement view) {
        ElementNode root = view.root();
        String controller = view.controllerFqcn();
        String controllerPackage = packageOf(controller);
        String className = view.generatedClassName();

        List<String> fxIdFields = new ArrayList<>();
        List<String> fieldDeclarations = new ArrayList<>();
        List<String> ctorBody = new ArrayList<>();
        List<String> childVars = new ArrayList<>();
        int anonCounter = 0;

        // Root field + children walk in document order
        fieldDeclarations.add("    private final " + controller + " controller = new " + controller + "();");
        String rootVar = declareNode(root, "root", fieldDeclarations);
        childVars.addAll(collectChildren(root, fieldDeclarations, anonCounter));

        // Static properties in document order (root first, then descendants)
        appendProperties(root, rootVar, ctorBody);
        for (ElementNode child : root.children()) {
            appendChildProperties(child, ctorBody);
        }

        // Child wiring
        if (!root.children().isEmpty()) {
            ctorBody.add("        " + rootVar + ".getChildren().addAll(" + String.join(", ", childVars) + ");");
        }

        // Controller field injection (document order, fx:id elements only)
        root.walk(element -> {
            String fxId = element.attribute("fx:id");
            if (fxId != null) {
                ctorBody.add("        controller." + fxId + " = " + varName(element) + ";");
            }
        });
        ctorBody.add("        controller.initialize();");

        // Imports based on what the view actually uses
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from ").append(view.sourceFile().getFileName())
                .append(" by jmifx-codegen — DO NOT EDIT\n");
        if (!controllerPackage.isEmpty()) {
            sb.append("package ").append(controllerPackage).append(";\n\n");
        }
        sb.append("import com.jmifx.FxView;\n");
        if (uses(root, "onAction")) {
            sb.append("import javafx.event.ActionEvent;\n");
        }
        if (uses(root, "alignment")) {
            sb.append("import javafx.geometry.Pos;\n");
        }
        sb.append("import javafx.scene.Parent;\n");
        java.util.Set<String> controlImports = new java.util.TreeSet<>();
        java.util.Set<String> layoutImports = new java.util.TreeSet<>();
        root.walk(element -> {
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
        });
        controlImports.forEach(sb::append);
        layoutImports.forEach(sb::append);
        sb.append('\n');
        sb.append("public class ").append(className).append(" implements FxView {\n\n");
        for (String field : fieldDeclarations) {
            sb.append(field).append('\n');
        }
        sb.append('\n');
        sb.append("    public ").append(className).append("() {\n");
        for (String statement : ctorBody) {
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

    private String declareNode(ElementNode element, String var, List<String> fields) {
        String spacing = element.attribute("spacing");
        String ctor = spacing != null
                ? "new " + element.tag() + "(" + Double.parseDouble(spacing) + ")"
                : "new " + element.tag() + "()";
        String fxId = element.attribute("fx:id");
        String name = fxId != null ? fxId : var;
        fields.add("    private final " + element.tag() + " " + name + " = " + ctor + ";");
        return name;
    }

    private List<String> collectChildren(ElementNode root, List<String> fields, int counter) {
        List<String> vars = new ArrayList<>();
        for (ElementNode child : root.children()) {
            String fxId = child.attribute("fx:id");
            String var = fxId != null ? fxId : child.tag().toLowerCase() + "_" + (++counter);
            String ctor = "new " + child.tag() + "()";
            fields.add("    private final " + child.tag() + " " + var + " = " + ctor + ";");
            vars.add(var);
        }
        return vars;
    }

    private void appendProperties(ElementNode element, String var, List<String> body) {
        for (Map.Entry<String, String> attr : element.attributes().entrySet()) {
            appendProperty(element, var, attr.getKey(), attr.getValue(), body);
        }
    }

    private void appendChildProperties(ElementNode element, List<String> body) {
        String var = varName(element);
        for (Map.Entry<String, String> attr : element.attributes().entrySet()) {
            appendProperty(element, var, attr.getKey(), attr.getValue(), body);
        }
        for (ElementNode grandChild : element.children()) {
            appendChildProperties(grandChild, body);
        }
    }

    private void appendProperty(ElementNode element, String var, String name, String value,
                                List<String> body) {
        switch (name) {
            case "text" -> body.add("        " + var + ".setText(\"" + escape(value) + "\");");
            case "promptText" -> body.add("        " + var + ".setPromptText(\"" + escape(value) + "\");");
            case "prefWidth" -> body.add("        " + var + ".setPrefWidth(" + Double.parseDouble(value) + ");");
            case "prefHeight" -> body.add("        " + var + ".setPrefHeight(" + Double.parseDouble(value) + ");");
            case "maxWidth" -> body.add("        " + var + ".setMaxWidth(" + Double.parseDouble(value) + ");");
            case "alignment" -> body.add("        " + var + ".setAlignment(Pos.valueOf(\"" + value + "\"));");
            case "onAction" -> body.add("        " + var + ".addEventHandler(ActionEvent.ACTION, controller::"
                    + value.replaceFirst("^#", "") + ");");
            default -> { /* fx:id / id / spacing (ctor) consumed elsewhere */ }
        }
    }

    private static boolean uses(ElementNode root, String attributeName) {
        boolean[] found = { false };
        root.walk(element -> {
            if (element.attribute(attributeName) != null) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static String varName(ElementNode element) {
        String fxId = element.attribute("fx:id");
        return fxId != null ? fxId : element.tag().toLowerCase();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String packageOf(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? "" : fqcn.substring(0, dot);
    }
}
