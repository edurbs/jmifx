package com.jmifx.codegen.model;

import java.nio.file.Path;

/**
 * A parsed FXML view: view id (file base name), the controller FQCN from
 * {@code fx:controller}, and the element tree.
 */
public record ViewElement(String viewId, String controllerFqcn, ElementNode root, Path sourceFile) {

    /**
     * Derived generated class name: kebab-case id to PascalCase, plus a "View"
     * suffix unless the PascalCase form already ends with "View" (so
     * "hello-view" → HelloView, not HelloViewView; "login" → LoginView).
     */
    public String generatedClassName() {
        String[] parts = viewId.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        String pascal = sb.toString();
        return pascal.endsWith("View") ? pascal : pascal + "View";
    }
}
