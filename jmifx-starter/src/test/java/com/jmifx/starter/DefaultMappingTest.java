package com.jmifx.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = DefaultMappingTest.App.class)
@AutoConfigureMockMvc
class DefaultMappingTest {

    @SpringBootApplication
    static class App {
    }

    @Autowired
    MockMvc mvc;

    @Test
    void servesIndexHtml() throws Exception {
        mvc.perform(get("/fx/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jmifx-starter test asset")));
    }

    @Test
    void forwardsRootToIndex() throws Exception {
        // MockMvc cannot re-dispatch a forward through resource handlers (no real
        // servlet container) — assert the forward target precisely instead; real
        // serving is covered by servesIndexHtml and the Task 11 browser E2E.
        mvc.perform(get("/fx/"))
                .andExpect(status().isOk())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals(
                        "/fx/index.html", result.getResponse().getForwardedUrl()));
        mvc.perform(get("/fx"))
                .andExpect(status().isOk())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals(
                        "/fx/index.html", result.getResponse().getForwardedUrl()));
    }

    @Test
    void serves404WhenAssetsMissing() throws Exception {
        mvc.perform(get("/fx/nope.wasm"))
                .andExpect(status().isNotFound());
    }

    @Test
    void servesAssetsWithNoCacheHeader() throws Exception {
        // the wasm filename never changes between builds — without no-cache a
        // normal reload can keep a stale client after redeploy (M2 incident)
        mvc.perform(get("/fx/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-cache")));
    }
}
