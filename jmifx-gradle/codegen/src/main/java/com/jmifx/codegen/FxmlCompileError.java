package com.jmifx.codegen;

import java.nio.file.Path;

/** A validation error, rendered as {@code <file>:<line>: <message>}. */
public record FxmlCompileError(Path file, int line, String message) {

    public String format() {
        return file + ":" + line + ": " + message;
    }
}
