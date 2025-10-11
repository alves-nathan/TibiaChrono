package com.nathan.tibiastats.api;

import com.nathan.tibiastats.AbstractPostgresTest;
import com.nathan.tibiastats.domain.model.ScrapeRecord;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.infrastructure.persistence.SpringWorldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OnlineControllerIntegrationTest extends AbstractPostgresTest {
    @Autowired MockMvc mvc;
    @Autowired SpringWorldRepository worlds;

    String token;

    @BeforeEach
    void setup() throws Exception {
        // ensure auth user
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"tester\",\"password\":\"secret\"}"));
        var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"secret\"}"))
                .andReturn();
        token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        // seed world + scrape
        var w = worlds.save(new World("Antica","Open PvP","Europe"));
        worlds.saveScrape(new ScrapeRecord(w, Instant.now(), 123, "[]"));
    }

    @Test
    void total_and_worlds_authenticated() throws Exception {
        mvc.perform(get("/api/online/total").header("Authorization","Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(0)));

        mvc.perform(get("/api/online/worlds").header("Authorization","Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].world", not(emptyString())))
                .andExpect(jsonPath("$[0].playersOnline", greaterThanOrEqualTo(0)));

        mvc.perform(get("/api/online/worlds/Antica").header("Authorization","Bearer "+token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.world", is("Antica")))
                .andExpect(jsonPath("$.playersOnline", is(123)));
    }

    @Test
    void unauthorized_without_token() throws Exception {
        mvc.perform(get("/api/online/total")).andExpect(status().isUnauthorized());
    }
}