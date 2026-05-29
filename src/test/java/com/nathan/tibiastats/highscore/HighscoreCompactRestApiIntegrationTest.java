package com.nathan.tibiastats.highscore;

import com.jayway.jsonpath.JsonPath;
import com.nathan.tibiastats.AbstractPostgresTest;
import com.nathan.tibiastats.domain.model.HighscoreStatRow;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.infrastructure.persistence.HighscoreStatRecordWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "tibiastats.scrape.highscores.enabled=false")
@AutoConfigureMockMvc
class HighscoreCompactRestApiIntegrationTest extends AbstractPostgresTest {
    @Autowired MockMvc mvc;
    @Autowired HighscoreStatRecordWriter writer;

    String token;

    @BeforeEach
    void setupAuth() throws Exception {
        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"highscore-api-tester\",\"password\":\"secret\"}"));
        var login = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"highscore-api-tester\",\"password\":\"secret\"}"))
                .andReturn();
        token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void exposesExperienceRanksDailyAndGainsFromCompactTables() throws Exception {
        Integer worldId = insertWorld("ApiWorld");
        Long slow = insertCharacter("Runner Slow");
        Long fast = insertCharacter("Runner Fast");
        LocalDate start = LocalDate.of(2026, 5, 27);
        LocalDate end = LocalDate.of(2026, 5, 28);

        writer.upsertBatch(List.of(
                row(slow, worldId, StatCategory.EXPERIENCE, 0, start, 1_000L, 2),
                row(fast, worldId, StatCategory.EXPERIENCE, 0, start, 1_000L, 1),
                row(slow, worldId, StatCategory.EXPERIENCE, 0, end, 1_200L, 2),
                row(fast, worldId, StatCategory.EXPERIENCE, 0, end, 1_900L, 1)
        ));

        mvc.perform(get("/api/highscores/exp/ranks")
                        .header("Authorization", "Bearer " + token)
                        .param("world", "ApiWorld")
                        .param("date", end.toString())
                        .param("vocationFilterId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].characterName", is("Runner Fast")))
                .andExpect(jsonPath("$[0].rank", is(1)))
                .andExpect(jsonPath("$[0].experience", is(1900)));

        mvc.perform(get("/api/highscores/exp/daily")
                        .header("Authorization", "Bearer " + token)
                        .param("world", "ApiWorld")
                        .param("date", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].experience", is(1900)));

        mvc.perform(get("/api/highscores/exp/gains")
                        .header("Authorization", "Bearer " + token)
                        .param("world", "ApiWorld")
                        .param("startDate", start.toString())
                        .param("endDate", end.toString())
                        .param("vocationFilterId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].characterName", is("Runner Fast")))
                .andExpect(jsonPath("$[0].gain", is(900)))
                .andExpect(jsonPath("$[0].startRank", is(1)))
                .andExpect(jsonPath("$[0].endRank", is(1)));
    }

    @Test
    void exposesCurrentAndHistoricalNonExperienceRecordsFromCompactTables() throws Exception {
        Integer worldId = insertWorld("SkillApiWorld");
        Long characterId = insertCharacter("Axe Master");
        LocalDate start = LocalDate.of(2026, 5, 27);
        LocalDate end = LocalDate.of(2026, 5, 28);

        writer.upsertBatch(List.of(
                row(characterId, worldId, StatCategory.AXE_FIGHTING, 0, start, 120L, 10),
                row(characterId, worldId, StatCategory.AXE_FIGHTING, 0, end, 121L, 8)
        ));

        mvc.perform(get("/api/highscores/current")
                        .header("Authorization", "Bearer " + token)
                        .param("world", "SkillApiWorld")
                        .param("category", "AXE_FIGHTING")
                        .param("vocationFilterId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].characterName", is("Axe Master")))
                .andExpect(jsonPath("$[0].category", is("AXE_FIGHTING")))
                .andExpect(jsonPath("$[0].rank", is(8)))
                .andExpect(jsonPath("$[0].value", is(121)));

        mvc.perform(get("/api/highscores/history")
                        .header("Authorization", "Bearer " + token)
                        .param("world", "SkillApiWorld")
                        .param("category", "AXE_FIGHTING")
                        .param("characterName", "Axe Master")
                        .param("vocationFilterId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].rank", is(8)))
                .andExpect(jsonPath("$[0].value", is(121)))
                .andExpect(jsonPath("$[1].rank", is(10)))
                .andExpect(jsonPath("$[1].validUntil", is(end.toString())));
    }

    @Test
    void characterHighscoresReadsCompactExperienceHistory() throws Exception {
        Integer worldId = insertWorld("CharacterApiWorld");
        Long characterId = insertCharacter("Character Runner");
        LocalDate date = LocalDate.of(2026, 5, 27);

        writer.upsertBatch(List.of(row(characterId, worldId, StatCategory.EXPERIENCE, 0, date, 5_000L, 50)));

        mvc.perform(get("/api/characters/{name}/highscores", "Character Runner")
                        .header("Authorization", "Bearer " + token)
                        .param("category", "EXPERIENCE")
                        .param("world", "CharacterApiWorld"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].characterName", is("Character Runner")))
                .andExpect(jsonPath("$[0].experience", is(5000)));
    }

    private HighscoreStatRow row(Long characterId,
                                                           Integer worldId,
                                                           StatCategory category,
                                                           int vocationFilterId,
                                                           LocalDate date,
                                                           long value,
                                                           int rank) {
        return new HighscoreStatRow(
                characterId,
                worldId,
                category,
                vocationFilterId,
                date,
                value,
                rank,
                Instant.parse(date + "T10:00:00Z")
        );
    }

    private Integer insertWorld(String name) {
        return jdbc.queryForObject(
                "insert into worlds(name, pvp_type, location) values (?, 'Open PvP', 'Europe') returning id",
                Integer.class,
                name
        );
    }

    private Long insertCharacter(String name) {
        Long id = jdbc.queryForObject("insert into characters(level) values (100) returning id", Long.class);
        jdbc.update("insert into character_names(character_id, name, active) values (?, ?, true)", id, name);
        return id;
    }
}
