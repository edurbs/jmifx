package com.jmifx;

/**
 * Static facade for view-to-view navigation, usable from FXML controllers the
 * way Jmix apps use {@code ViewNavigators}. {@link FxApplication#start} binds
 * the app's navigator; until then navigation throws.
 */
public final class FxNavigation {

    private static volatile FxNavigator navigator;

    private FxNavigation() {
    }

    /** Bound by {@link FxApplication#start}; package-private (tests may reset with null). */
    static void bind(FxNavigator bound) {
        navigator = bound;
    }

    public static void navigateTo(String viewId) {
        FxNavigator n = navigator;
        if (n == null) {
            throw new IllegalStateException("FxNavigation not bound — FxApplication.start must run first");
        }
        n.navigateTo(viewId);
    }
}
