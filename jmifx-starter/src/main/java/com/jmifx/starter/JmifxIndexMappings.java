package com.jmifx.starter;

import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.ParameterizableViewController;

import java.util.Map;

/**
 * Maps the client root ({@code /fx} and {@code /fx/}) to a forward onto the
 * loader page so users can open the app without typing a file name. Built
 * programmatically because annotation mappings cannot resolve defaults from
 * {@link JmifxProperties}.
 */
public final class JmifxIndexMappings {

    private JmifxIndexMappings() {
    }

    public static SimpleUrlHandlerMapping indexForwardMapping(JmifxProperties properties) {
        ParameterizableViewController forward = new ParameterizableViewController();
        forward.setViewName("forward:" + properties.getPath() + "/index.html");
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping(Map.of(
                properties.getPath(), forward,
                properties.getPath() + "/", forward));
        // must precede the resource-handler mapping (order MAX_VALUE-1)
        mapping.setOrder(Integer.MAX_VALUE - 10);
        return mapping;
    }
}
