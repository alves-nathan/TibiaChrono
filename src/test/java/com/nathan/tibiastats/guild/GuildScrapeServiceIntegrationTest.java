package com.nathan.tibiastats.guild;

import com.nathan.tibiastats.AbstractPostgresTest;
import com.nathan.tibiastats.application.service.GuildScrapeService;
import com.nathan.tibiastats.domain.port.GuildScrapePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "tibiastats.scrape.guilds.enabled=false",
        "tibiastats.scrape.highscores.enabled=false"
})
class GuildScrapeServiceIntegrationTest extends AbstractPostgresTest {
    @Autowired GuildScrapeService service;
    @Autowired FakeGuildScrapePort fake;

    @BeforeEach
    void setupFake() {
        fake.clear();
    }

    @Test
    void opensMembershipAndJoinEventWhenPlayerIsFirstSeenInGuild() {
        fake.putDetail(detail("Raw Raw", member("Nathan Test", "Leader", "Boss", "Elite Knight", 500)));

        GuildScrapeService.GuildDetailResult result = service.updateGuildDetail("Raw Raw");

        assertThat(result.membersSeen()).isEqualTo(1);
        assertThat(result.membershipsOpened()).isEqualTo(1);
        assertThat(result.membershipsClosed()).isEqualTo(0);

        Integer activeMemberships = jdbc.queryForObject("select count(*) from guild_memberships where active is true", Integer.class);
        Integer joinEvents = jdbc.queryForObject("select count(*) from guild_membership_events where event_type = 'JOINED'", Integer.class);
        String guildName = jdbc.queryForObject("select g.name from guild_memberships gm join guilds g on g.id = gm.guild_id where gm.active is true", String.class);

        assertThat(activeMemberships).isEqualTo(1);
        assertThat(joinEvents).isEqualTo(1);
        assertThat(guildName).isEqualTo("Raw Raw");
    }

    @Test
    void closesMembershipAndAddsLeftEventWhenPlayerDisappearsFromGuildSnapshot() {
        fake.putDetail(detail("Raw Raw", member("Former Raw", "Member", null, "Royal Paladin", 300)));
        service.updateGuildDetail("Raw Raw");

        fake.putDetail(detail("Raw Raw"));
        GuildScrapeService.GuildDetailResult result = service.updateGuildDetail("Raw Raw");

        assertThat(result.membershipsClosed()).isEqualTo(1);

        Integer activeMemberships = jdbc.queryForObject("select count(*) from guild_memberships where active is true", Integer.class);
        Integer inactiveMemberships = jdbc.queryForObject("select count(*) from guild_memberships where active is false and left_at is not null", Integer.class);
        Integer leftEvents = jdbc.queryForObject("select count(*) from guild_membership_events where event_type = 'LEFT'", Integer.class);

        assertThat(activeMemberships).isZero();
        assertThat(inactiveMemberships).isEqualTo(1);
        assertThat(leftEvents).isEqualTo(1);
    }

    @Test
    void transfersPlayerWhenSameCharacterIsSeenInAnotherGuild() {
        fake.putDetail(detail("Raw Raw", member("Transfer Test", "Member", null, "Elder Druid", 250)));
        service.updateGuildDetail("Raw Raw");

        fake.putDetail(detail("Other Guild", member("Transfer Test", "Vice Leader", "Recruiter", "Elder Druid", 251)));
        GuildScrapeService.GuildDetailResult result = service.updateGuildDetail("Other Guild");

        assertThat(result.transfers()).isEqualTo(1);
        assertThat(result.membershipsOpened()).isEqualTo(1);
        assertThat(result.membershipsClosed()).isEqualTo(1);

        String activeGuild = jdbc.queryForObject("""
                select g.name
                  from guild_memberships gm
                  join guilds g on g.id = gm.guild_id
                 where gm.active is true
                """, String.class);
        Integer transferredEvents = jdbc.queryForObject("select count(*) from guild_membership_events where event_type = 'TRANSFERRED'", Integer.class);
        Integer inactiveOldGuilds = jdbc.queryForObject("""
                select count(*)
                  from guild_memberships gm
                  join guilds g on g.id = gm.guild_id
                 where gm.active is false
                   and gm.left_at is not null
                   and g.name = 'Raw Raw'
                """, Integer.class);

        assertThat(activeGuild).isEqualTo("Other Guild");
        assertThat(transferredEvents).isEqualTo(1);
        assertThat(inactiveOldGuilds).isEqualTo(1);
    }

    private static GuildScrapePort.GuildDetail detail(String guildName, GuildScrapePort.Member... members) {
        return new GuildScrapePort.GuildDetail(
                guildName,
                "Antica",
                "Test guild",
                null,
                null,
                LocalDate.of(2026, 5, 27),
                members.length,
                0,
                guildName + "-hash-" + members.length,
                List.of(members),
                List.of()
        );
    }

    private static GuildScrapePort.Member member(String name, String rank, String title, String vocation, Integer level) {
        return new GuildScrapePort.Member(name, rank, title, vocation, level, null, false);
    }

    @TestConfiguration
    static class FakeConfig {
        @Bean
        @Primary
        FakeGuildScrapePort fakeGuildScrapePort() {
            return new FakeGuildScrapePort();
        }
    }

    static class FakeGuildScrapePort implements GuildScrapePort {
        private final Map<String, GuildDetail> details = new ConcurrentHashMap<>();

        void putDetail(GuildDetail detail) {
            details.put(detail.name().toLowerCase(java.util.Locale.ROOT), detail);
        }

        void clear() {
            details.clear();
        }

        @Override
        public List<GuildListItem> fetchGuildList(String worldName) {
            return details.values().stream()
                    .map(d -> new GuildListItem(d.name(), d.worldName(), true, d.description()))
                    .toList();
        }

        @Override
        public GuildDetail fetchGuildDetail(String guildName) {
            GuildDetail detail = details.get(guildName.toLowerCase(java.util.Locale.ROOT));
            if (detail == null) {
                throw new IllegalArgumentException("No fake guild detail configured for " + guildName);
            }
            return detail;
        }
    }
}
