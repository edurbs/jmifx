package com.jmifx.starter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CustomPathTest.App.class,
        properties = "jmifx.path=/ui")
@AutoConfigureMockMvc
class CustomPathTest {

    @SpringBootApplication
    static class App {
    }

    @Autowired
    MockMvc mvc;

    @Test
    void customPathIsHonored() throws Exception {
        mvc.perform(get("/ui/index.html"))
                .andExpect(status().isOk());
    }
}
