package com.nathan.tibiastats.api;

import com.jayway.jsonpath.JsonPath;
import com.nathan.tibiastats.AbstractPostgresTest;
import com.nathan.tibiastats.application.service.CharacterNamingService;
import com.nathan.tibiastats.domain.model.CharacterEntity;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CharacterTimelineIntegrationTest extends AbstractPostgresTest {
    @Autowired MockMvc mvc;
    @Autowired SpringWorldRepository worlds;
    @Autowired CharacterNamingService naming;

    String token;
    Instant base;
    CharacterEntity character;
    World antica;

    @BeforeEach
    void setup() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"timeline-tester\",\"password\":\"secret\"}"));
        var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"timeline-tester\",\"password\":\"secret\"}"))
                .andReturn();
        token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        base = Instant.parse("2026-05-28T10:00:00Z");
        antica = worlds.save(new World("Antica", "Open PvP", "Europe"));
        character = naming.ensureCharacterForName("Timeline Hero", "Timeline Hero");

        jdbc.update("""
                update characters
                   set creation_date = ?,
                       last_login = ?,
                       details_last_scraped_at = ?,
                       details_last_scrape_status = 'SUCCESS'
                 where id = ?
                """,
                Timestamp.from(base.minusSeconds(60 * 60)),
                Timestamp.from(base.minusSeconds(30 * 60)),
                Timestamp.from(base.minusSeconds(20 * 60)),
                character.getId());

        jdbc.update("insert into character_deaths(character_id, death_date, killed_by) values (?, ?, ?)",
                character.getId(), Timestamp.from(base.plusSeconds(15 * 60)), "a dragon");

        jdbc.update("insert into character_worlds(character_id, world_id, active, inactive_date) values (?, ?, false, ?)",
                character.getId(), antica.getId(), Timestamp.from(base.plusSeconds(10 * 60)));

        Long guildId = jdbc.queryForObject("""
                insert into guilds(name, normalized_name, world_id, active)
                values ('Timeline Guild', 'timeline guild', ?, true)
                returning id
                """, Long.class, antica.getId());

        jdbc.update("""
                insert into guild_memberships(
                    guild_id, character_id, character_name_snapshot, rank_name, title, vocation, level,
                    joined_at, first_seen_at, last_seen_at, left_at, active
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false)
                """,
                guildId, character.getId(), "Timeline Hero", "Leader", "Founder", "Elite Knight", 100,
                Timestamp.from(base.plusSeconds(5 * 60)),
                Timestamp.from(base.plusSeconds(5 * 60)),
                Timestamp.from(base.plusSeconds(30 * 60)),
                Timestamp.from(base.plusSeconds(30 * 60)));

        jdbc.update("""
                insert into guild_membership_events(
                    character_id, character_name_snapshot, event_type, to_guild_id, observed_at, description
                ) values (?, ?, 'JOINED', ?, ?, ?)
                """,
                character.getId(), "Timeline Hero", guildId,
                Timestamp.from(base.plusSeconds(5 * 60)), "Timeline Hero joined Timeline Guild");

        jdbc.update("""
                insert into highscore_exp_daily(date, character_id, world_id, level, experience, vocation_id, first_seen_filter, scraped_at)
                values (?, ?, ?, 100, 123456789, 4, 0, ?)
                """, LocalDate.parse("2026-05-28"), character.getId(), antica.getId(), Timestamp.from(base.plusSeconds(40 * 60)));
        jdbc.update("""
                insert into highscore_exp_rank_daily(date, character_id, world_id, vocation_filter_id, rank, scraped_at)
                values (?, ?, ?, 0, 50, ?)
                """, LocalDate.parse("2026-05-28"), character.getId(), antica.getId(), Timestamp.from(base.plusSeconds(40 * 60)));

        saveOnlinePoint(antica, character, base.plusSeconds(80 * 60), 120);
        saveOnlinePoint(antica, character, base.plusSeconds(81 * 60), 121);
    }

    @Test
    void deaths_endpoint_returns_character_deaths() throws Exception {
        mvc.perform(get("/api/characters/{name}/deaths", "Timeline Hero")
                        .header("Authorization", "Bearer " + token)
                        .param("from", base.toString())
                        .param("to", base.plusSeconds(60 * 60).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].characterName", is("Timeline Hero")))
                .andExpect(jsonPath("$[0].killedBy", is("a dragon")));
    }

    @Test
    void world_and_guild_history_endpoints_return_character_history() throws Exception {
        mvc.perform(get("/api/characters/{name}/world-history", "Timeline Hero")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].world", is("Antica")))
                .andExpect(jsonPath("$[0].active", is(false)));

        mvc.perform(get("/api/characters/{name}/guild-history", "Timeline Hero")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].guildName", is("Timeline Guild")))
                .andExpect(jsonPath("$[0].rankName", is("Leader")))
                .andExpect(jsonPath("$[0].active", is(false)));
    }

    @Test
    void timeline_returns_core_highscore_and_online_events() throws Exception {
        mvc.perform(get("/api/characters/{name}/timeline", "Timeline Hero")
                        .header("Authorization", "Bearer " + token)
                        .param("from", base.minusSeconds(24 * 60 * 60).toString())
                        .param("to", base.plusSeconds(2 * 60 * 60).toString())
                        .param("includeOnlineSessions", "true")
                        .param("includeHighscores", "true")
                        .param("maxGapMinutes", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItem("CHARACTER_CREATED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("GUILD_JOINED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("WORLD_LEFT")))
                .andExpect(jsonPath("$[*].eventType", hasItem("DEATH")))
                .andExpect(jsonPath("$[*].eventType", hasItem("HIGHSCORE_EXPERIENCE")))
                .andExpect(jsonPath("$[*].eventType", hasItem("ONLINE_SESSION")))
                .andExpect(jsonPath("$[0].eventType", is("ONLINE_SESSION")))
                .andExpect(jsonPath("$[0].metadata.samples", is(2)));
    }

    @Test
    void timeline_invalid_range_returns_bad_request() throws Exception {
        mvc.perform(get("/api/characters/{name}/timeline", "Timeline Hero")
                        .header("Authorization", "Bearer " + token)
                        .param("from", base.plusSeconds(60).toString())
                        .param("to", base.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeline_missing_character_returns_not_found() throws Exception {
        mvc.perform(get("/api/characters/{name}/timeline", "Unknown")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private void saveOnlinePoint(World world, CharacterEntity character, Instant timestamp, int playersOnline) {
        Scrape scrape = new Scrape();
        scrape.setWorld(world);
        scrape.setScrapeTime(timestamp);
        scrape.setPlayersOnline(playersOnline);
        scrape.addPlayer(new ScrapePlayer(null, character));
        worlds.saveScrape(scrape);
    }
}
