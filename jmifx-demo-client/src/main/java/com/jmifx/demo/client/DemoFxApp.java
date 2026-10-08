package com.jmifx.demo.client;

import com.jmifx.FxApplication;
import com.jmifx.FxViewRegistry;
import com.jmifx.generated.FxViewsIndex;

public class DemoFxApp extends FxApplication {

    @Override
    protected String getStartupViewId() {
        return "login-view";
    }

    @Override
    protected void registerViews(FxViewRegistry registry) {
        FxViewsIndex.registerAll(registry);
    }

    public static void main(String[] args) {
        launchApp(DemoFxApp::new, args);
    }
}
