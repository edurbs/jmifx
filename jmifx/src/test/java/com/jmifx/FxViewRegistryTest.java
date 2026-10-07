package com.jmifx;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class FxViewRegistryTest {

    private final FxViewRegistry registry = new FxViewRegistry();

    @Test
    void registerAndCreateView() {
        registry.register("hello-view", StubView::new);

        assertTrue(registry.hasView("hello-view"));
        assertEquals(java.util.Set.of("hello-view"), registry.getViewIds());
        FxView view = registry.createView("hello-view");
        assertEquals("hello-view", view.getId());
    }

    @Test
    void createViewEachCallReturnsNewInstance() {
        registry.register("v", StubView::new);

        assertNotSame(registry.createView("v"), registry.createView("v"));
    }

    @Test
    void unknownIdThrowsWithExactMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.createView("missing"));
        assertEquals("No view registered for id 'missing'", ex.getMessage());
    }

    @Test
    void duplicateIdThrows() {
        registry.register("v", StubView::new);
        assertThrows(IllegalArgumentException.class, () -> registry.register("v", StubView::new));
    }

    @Test
    void hasViewFalseForUnknownId() {
        assertFalse(registry.hasView("nope"));
    }

    private static final class StubView implements FxView {
        @Override public String getId() { return "hello-view"; }
        @Override public javafx.scene.Parent getRoot() { return new Pane(); }
    }
}
