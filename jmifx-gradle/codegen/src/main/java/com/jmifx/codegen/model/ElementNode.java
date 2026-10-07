package com.jmifx.codegen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One FXML element with its source line number, attributes (qualified names as
 * written, e.g. {@code fx:id}) and child elements. Accessor-style methods match
 * the record shape callers use: {@code tag()}, {@code line()}, {@code attributes()},
 * {@code children()}.
 */
public final class ElementNode {

    private final String tag;
    private final int line;
    private final Map<String, String> attributes;
    private final List<ElementNode> children;

    public static final class Builder {
        private final String tag;
        private final int line;
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private final List<Builder> childBuilders = new ArrayList<>();

        public Builder(String tag, int line, Map<String, String> attributes) {
            this.tag = tag;
            this.line = line;
            this.attributes.putAll(attributes);
        }

        public void addChild(Builder child) {
            childBuilders.add(child);
        }

        public ElementNode build() {
            List<ElementNode> builtChildren = childBuilders.stream()
                    .map(Builder::build)
                    .toList();
            return new ElementNode(tag, line, attributes, builtChildren);
        }
    }

    private ElementNode(String tag, int line, Map<String, String> attributes, List<ElementNode> children) {
        this.tag = tag;
        this.line = line;
        // LinkedHashMap to preserve FXML document order (emission order matters)
        this.attributes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.children = List.copyOf(children);
    }

    public String tag() {
        return tag;
    }

    public int line() {
        return line;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public List<ElementNode> children() {
        return children;
    }

    public String attribute(String name) {
        return attributes.get(name);
    }

    /** Depth-first walk over this element and all descendants. */
    public void walk(java.util.function.Consumer<ElementNode> visitor) {
        visitor.accept(this);
        children.forEach(c -> c.walk(visitor));
    }
}
