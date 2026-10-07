package com.jmifx;

import java.util.function.Supplier;

/** Stub of the real FxViewRegistry (see {@link FxView} for why this exists). */
public final class FxViewRegistry {

    public void register(String id, Supplier<FxView> factory) {
        // no-op for codegen integration testing
    }
}
