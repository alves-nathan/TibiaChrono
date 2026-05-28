package com.nathan.tibiastats.api;

import com.jayway.jsonpath.JsonPath;
import com.nathan.tibiastats.AbstractPostgresTest;
import com.nathan.tibiastats.domain.model.Scrape;
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

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorldOnlineAnalyticsIntegrationTest extends AbstractPostgresTest {
    @Autowired MockMvc mvc;
    @Autowired SpringWorldRepository worlds;

    String token;
    Instant base;

    @BeforeEach
    void setup() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"world-analytics-tester\",\"password\":\"secret\"}"));
        var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"world-analytics-tester\",\"password\":\"secret\"}"))
                .andReturn();
        token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        base = Instant.parse("2026-05-28T10:00:00Z");
        var antica = worlds.save(new World("Antica", "Open PvP", "Europe"));
        var secura = worlds.save(new World("Secura", "Optional PvP", "Europe"));

        saveOnlinePoint(antica, base, 100);
        saveOnlinePoint(antica, base.plusSeconds(30 * 60), 140);
        saveOnlinePoint(antica, base.plusSeconds(60 * 60), 160);

        saveOnlinePoint(secura, base, 70);
        saveOnlinePoint(secura, base.plusSeconds(60 * 60), 90);
    }

    @Test
    void world_online_buckets_group_scrapes_by_hour() throws Exception {
        mvc.perform(get("/api/analytics/worlds/{world}/online/buckets", "Antica")
                        .header("Authorization", "Bearer " + token)
                        .param("from", base.minusSeconds(60).toString())
                        .param("to", base.plusSeconds(2 * 60 * 60).toString())
                        .param("bucket", "hour"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].world", is("Antica")))
                .andExpect(jsonPath("$[0].samples", is(2)))
                .andExpect(jsonPath("$[0].averagePlayersOnline", closeTo(120.0, 0.01)))
                .andExpect(jsonPath("$[0].minPlayersOnline", is(100)))
                .andExpect(jsonPath("$[0].maxPlayersOnline", is(140)))
                .andExpect(jsonPath("$[0].changePlayersOnline", is(40)))
                .andExpect(jsonPath("$[1].samples", is(1)))
                .andExpect(jsonPath("$[1].maxPlayersOnline", is(160)));
    }

    @Test
    void world_online_summary_returns_aggregate_metrics() throws Exception {
        mvc.perform(get("/api/analytics/worlds/{world}/online/summary", "Antica")
                        .header("Authorization", "Bearer " + token)
                        .param("from", base.minusSeconds(60).toString())
                        .param("to", base.plusSeconds(2 * 60 * 60).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.world", is("Antica")))
                .andExpect(jsonPath("$.samples", is(3)))
                .andExpect(jsonPath("$.averagePlayersOnline", closeTo(133.333, 0.01)))
                .andExpect(jsonPath("$.peakPlayersOnline", is(160)))
                .andExpect(jsonPath("$.firstPlayersOnline", is(100)))
                .andExpect(jsonPath("$.latestPlayersOnline", is(160)))
                .andExpect(jsonPath("$.changePlayersOnline", is(60)));
    }

    @Test
    void compare_worlds_returns_bucketed_series_for_selected_worlds() throws Exception {
        mvc.perform(get("/api/analytics/worlds/compare")
                        .header("Authorization", "Bearer " + token)
                        .param("worlds", "Antica,Secura")
                        .param("from", base.minusSeconds(60).toString())
                        .param("to", base.plusSeconds(2 * 60 * 60).toString())
                        .param("bucket", "hour"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].world", is("Antica")))
                .andExpect(jsonPath("$[2].world", is("Secura")))
                .andExpect(jsonPath("$[2].maxPlayersOnline", is(70)));
    }

    @Test
    void ranking_orders_worlds_by_requested_metric() throws Exception {
        mvc.perform(get("/api/analytics/worlds/ranking")
                        .header("Authorization", "Bearer " + token)
                        .param("from", base.minusSeconds(60).toString())
                        .param("to", base.plusSeconds(2 * 60 * 60).toString())
                        .param("metric", "peak"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].world", is("Antica")))
                .andExpect(jsonPath("$[0].metric", is("peak")))
                .andExpect(jsonPath("$[0].metricValue", closeTo(160.0, 0.01)))
                .andExpect(jsonPath("$[1].world", is("Secura")));
    }

    @Test
    void invalid_range_returns_bad_request() throws Exception {
        mvc.perform(get("/api/analytics/worlds/{world}/online/summary", "Antica")
                        .header("Authorization", "Bearer " + token)
                        .param("from", base.plusSeconds(10).toString())
                        .param("to", base.toString()))
                .andExpect(status().isBadRequest());
    }

    private void saveOnlinePoint(World world, Instant timestamp, int playersOnline) {
        worlds.saveScrape(new Scrape(world, timestamp, playersOnline, "[]"));
    }
}
