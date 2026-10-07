package com.jmifx;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Registry of view factories, populated at application start-up by the
 * generated {@code com.jmifx.generated.FxViewsIndex}.
 */
public final class FxViewRegistry {

    private final Map<String, Supplier<FxView>> factories = new HashMap<>();

    /** Registers a factory under the given view id. Fails on duplicate ids. */
    public void register(String id, Supplier<FxView> factory) {
        Supplier<FxView> previous = factories.putIfAbsent(id, factory);
        if (previous != null) {
            throw new IllegalArgumentException("View id '" + id + "' is already registered");
        }
    }

    public boolean hasView(String id) {
        return factories.containsKey(id);
    }

    /** Creates a new view instance; never returns a cached view. */
    public FxView createView(String id) {
        Supplier<FxView> factory = factories.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("No view registered for id '" + id + "'");
        }
        return factory.get();
    }

    public Set<String> getViewIds() {
        return Set.copyOf(factories.keySet());
    }
}
