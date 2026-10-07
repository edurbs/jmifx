package com.jmifx.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DisabledTest.App.class,
        properties = "jmifx.enabled=false")
@AutoConfigureMockMvc
class DisabledTest {

    @SpringBootApplication
    static class App {
    }

    @Autowired
    MockMvc mvc;

    @Test
    void disabledPropertyDisablesServing() throws Exception {
        mvc.perform(get("/fx/index.html"))
                .andExpect(status().isNotFound());
    }
}
