package com.nathan.tibiastats.api;

import com.jayway.jsonpath.JsonPath;
import com.nathan.tibiastats.AbstractPostgresTest;
import com.nathan.tibiastats.application.service.CharacterNamingService;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.ScrapePlayer;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CharacterOnlineActivityIntegrationTest extends AbstractPostgresTest {
    @Autowired MockMvc mvc;
    @Autowired SpringWorldRepository worlds;
    @Autowired CharacterNamingService naming;

    String token;
    Instant base;

    @BeforeEach
    void setup() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"online-tester\",\"password\":\"secret\"}"));
        var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"online-tester\",\"password\":\"secret\"}"))
                .andReturn();
        token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        var world = worlds.save(new World("Antica", "Open PvP", "Europe"));
        var character = naming.ensureCharacterForName("Knight Sample", "Knight Sample");
        base = Instant.parse("2026-05-28T10:00:00Z");

        saveOnlinePoint(world, character, base, 120);
        saveOnlinePoint(world, character, base.plusSeconds(60), 121);
        saveOnlinePoint(world, character, base.plusSeconds(25 * 60), 130);
    }

    @Test
    void character_online_history_returns_scrape_points() throws Exception {
        mvc.perform(get("/api/characters/{name}/online-history", "Knight Sample")
                        .header("Authorization", "Bearer " + token)
                        .param("from", base.minusSeconds(60).toString())
                        .param("to", base.plusSeconds(30 * 60).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].characterName", is("Knight Sample")))
                .andExpect(jsonPath("$[0].world", is("Antica")))
                .andExpect(jsonPath("$[0].playersOnline", is(120)));
    }

    @Test
    void character_online_sessions_group_points_by_gap() throws Exception {
        mvc.perform(get("/api/characters/{name}/online-sessions", "Knight Sample")
                        .header("Authorization", "Bearer " + token)
                        .param("from", base.minusSeconds(60).toString())
                        .param("to", base.plusSeconds(30 * 60).toString())
                        .param("maxGapMinutes", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].world", is("Antica")))
                .andExpect(jsonPath("$[0].samples", is(1)))
                .andExpect(jsonPath("$[1].samples", is(2)))
                .andExpect(jsonPath("$[1].observedMinutes", is(1)));
    }

    @Test
    void character_activity_summary_aggregates_history_and_sessions() throws Exception {
        mvc.perform(get("/api/characters/{name}/activity-summary", "Knight Sample")
                        .header("Authorization", "Bearer " + token)
                        .param("from", base.minusSeconds(60).toString())
                        .param("to", base.plusSeconds(30 * 60).toString())
                        .param("maxGapMinutes", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterName", is("Knight Sample")))
                .andExpect(jsonPath("$.appearances", is(3)))
                .andExpect(jsonPath("$.sessions", is(2)))
                .andExpect(jsonPath("$.observedMinutes", is(1)))
                .andExpect(jsonPath("$.worlds[0].world", is("Antica")))
                .andExpect(jsonPath("$.worlds[0].appearances", is(3)));
    }

    @Test
    void missing_character_returns_not_found() throws Exception {
        mvc.perform(get("/api/characters/{name}/online-history", "Unknown")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private void saveOnlinePoint(World world,
                                 com.nathan.tibiastats.domain.model.CharacterEntity character,
                                 Instant timestamp,
                                 int playersOnline) {
        Scrape scrape = new Scrape();
        scrape.setWorld(world);
        scrape.setScrapeTime(timestamp);
        scrape.setPlayersOnline(playersOnline);
        scrape.addPlayer(new ScrapePlayer(null, character));
        worlds.saveScrape(scrape);
    }
}
