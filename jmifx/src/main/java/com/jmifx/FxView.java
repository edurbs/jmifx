package com.jmifx;

/**
 * A jmifx view: a JavaFX subtree identified by a view id. Views are produced by
 * the generated view classes (see jmifx-codegen) — one per FXML file.
 */
public interface FxView {

    /** The view id — by convention the FXML file base name without extension. */
    String getId();

    /** The root of this view's scene graph. */
    javafx.scene.Parent getRoot();
}
