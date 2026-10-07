package com.jmifx;

/**
 * Stub of the jmifx framework interface so codegen's integration test can
 * compile generated sources without a dependency on the jmifx module (the two
 * live in different Gradle builds). The REAL interface is pinned by golden
 * files and jmifx's own tests; Task 7's functional test links them for real.
 */
public interface FxView {

    String getId();

    javafx.scene.Parent getRoot();
}
