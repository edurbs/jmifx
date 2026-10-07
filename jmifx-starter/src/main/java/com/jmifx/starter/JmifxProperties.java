package com.jmifx.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** jmifx server-side configuration. */
@ConfigurationProperties(prefix = "jmifx")
public class JmifxProperties {

    /** Whether the compiled wasm client should be served. Default true. */
    private boolean enabled = true;

    /** Path the client is served under. Default /fx. */
    private String path = "/fx";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
