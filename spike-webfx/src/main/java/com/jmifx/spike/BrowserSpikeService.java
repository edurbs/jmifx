package com.jmifx.spike;

/** ServiceLoader provider — wasm/browser side. */
public class BrowserSpikeService implements SpikeService {
    @Override
    public String name() {
        return "browser";
    }
}
