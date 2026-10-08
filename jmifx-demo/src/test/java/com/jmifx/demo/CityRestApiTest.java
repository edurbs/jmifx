package com.jmifx.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmifx.demo.entity.City;
import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the client-facing contract: password grant with admin/admin, secured
 * City create (201 + row), 401 without token, 4xx on wrong credentials.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CityRestApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UnconstrainedDataManager dataManager;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void passwordGrantAndCreateCityReturns201() throws Exception {
        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("jmifx", "jmifx-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokenJson = json.readTree(tokenResult.getResponse().getContentAsString());
        String token = tokenJson.get("access_token").asText();

        MvcResult cityResult = mockMvc.perform(post("/rest/entities/City")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Springfield\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode cityJson = json.readTree(cityResult.getResponse().getContentAsString());
        // pins the REST entity name: bare "City", no project prefix
        assertEquals("City", cityJson.get("_entityName").asText());

        City loaded = dataManager.load(City.class)
                .id(UUID.fromString(cityJson.get("id").asText()))
                .one();
        assertEquals("Springfield", loaded.getName());
    }

    @Test
    void createWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/rest/entities/City")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nowhere\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("jmifx", "jmifx-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "admin")
                        .param("password", "wrong"))
                .andExpect(status().is4xxClientError());
    }
}
