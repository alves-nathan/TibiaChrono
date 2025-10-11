package com.nathan.tibiastats.auth;

import com.nathan.tibiastats.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractPostgresTest {
    @Autowired MockMvc mvc;

    @Test
    void register_login_refresh_logout_blacklist() throws Exception {
        // Register
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"secret\"}"))
                .andExpect(status().isOk());

        // Login → tokens
        var login = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyString())))
                .andReturn();

        String access = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        String refresh = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.refreshToken");

        // Refresh → new pair
        var refreshed = mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\""+refresh+"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyString())))
                .andReturn();

        String access2 = com.jayway.jsonpath.JsonPath.read(refreshed.getResponse().getContentAsString(), "$.accessToken");

        // Logout (blacklist access2)
        mvc.perform(post("/auth/logout").header("Authorization","Bearer "+access2))
                .andExpect(status().isOk());
    }
}