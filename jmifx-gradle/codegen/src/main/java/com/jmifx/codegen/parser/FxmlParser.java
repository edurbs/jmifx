package com.jmifx.codegen.parser;

import com.jmifx.codegen.model.ElementNode;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SAX-based FXML reader that keeps source line numbers on every element.
 * Namespace handling is off: qualified names ({@code VBox}, {@code fx:controller})
 * are used exactly as written.
 */
public final class FxmlParser {

    /** Parses one FXML file into an element tree; throws on malformed XML. */
    public ElementNode parse(Path fxmlFile) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            TreeBuilder builder = new TreeBuilder();
            parser.parse(fxmlFile.toFile(), builder);
            return builder.root;
        } catch (SAXException | IOException e) {
            throw new FxmlParseException("Malformed FXML: " + fxmlFile, e);
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new IllegalStateException("SAX parser not configurable", e);
        }
    }

    public static final class FxmlParseException extends RuntimeException {
        public FxmlParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class TreeBuilder extends DefaultHandler {
        private final Deque<ElementNode.Builder> stack = new ArrayDeque<>();
        private Locator locator;
        private ElementNode root;
        private final StringBuilder pendingText = new StringBuilder();

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            Map<String, String> attrs = new LinkedHashMap<>();
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = attributes.getQName(i);
                if (name.startsWith("xmlns")) {
                    continue;
                }
                attrs.put(name, attributes.getValue(i));
            }
            ElementNode.Builder builder = new ElementNode.Builder(qName, locator.getLineNumber(), attrs);
            if (!stack.isEmpty()) {
                stack.peek().addChild(builder);
            }
            stack.push(builder);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            ElementNode.Builder finished = stack.pop();
            String text = pendingText.toString().strip();
            if (!text.isEmpty() && finished.textContent() == null) {
                finished.setText(text, locator.getLineNumber());
            }
            pendingText.setLength(0);
            if (stack.isEmpty()) {
                root = finished.build();
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (!stack.isEmpty()) {
                pendingText.append(ch, start, length);
            }
        }
    }
}
