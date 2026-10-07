package com.jmifx.codegen;

import com.jmifx.codegen.model.ViewElement;

import java.util.List;

/** Parse + validation outcome; {@link #success()} is true only with zero errors. */
public record CompilationResult(List<FxmlCompileError> errors, List<ViewElement> views) {

    public boolean success() {
        return errors.isEmpty();
    }
}
