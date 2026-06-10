package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.GuildMembership;
import com.nathan.tibiastats.domain.model.GuildMembershipEvent;
import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
import com.nathan.tibiastats.domain.model.World;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryMappingTailCoverageTest {
    @Test
    void worldOnlineAnalyticsSupportCoversRangesLimitsEnumsAndSuccessfulWorldParsing() {
        TestSupport support = new TestSupport();
        Instant from = Instant.parse("2026-06-05T10:00:00Z");
        Instant to = Instant.parse("2026-06-05T12:00:00Z");
        StringBuilder sql = new StringBuilder("where 1=1\n");
        MapSqlParameterSource params = new MapSqlParameterSource();

        support.validateRange(from, to);
        support.appendRange(sql, params, from, to);

        assertThat(sql).contains("s.scrape_time >= :from").contains("s.scrape_time <= :to");
        assertThat(params.getValue("from")).isEqualTo(Timestamp.from(from));
        assertThat(params.getValue("to")).isEqualTo(Timestamp.from(to));
        assertThat(support.parseWorlds("Antica, antica, Secura")).containsExactly("antica", "secura");
        assertThat(WorldOnlineAnalyticsJdbcSupport.OnlineBucket.from("day").sqlValue()).isEqualTo("day");
        assertThat(WorldOnlineAnalyticsJdbcSupport.OnlineRankingMetric.from("growth").apiValue()).isEqualTo("growth");
        assertThat(WorldOnlineAnalyticsJdbcSupport.OnlineRankingMetric.from("growth").sqlExpression())
                .contains("latest_players_online");

        assertThatThrownBy(() -> support.validateRange(to, from))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("from must be before");
    }

    @Test
    void worldOnlineAnalyticsSupportMapsBucketSummaryAndRankingRows() throws Exception {
        TestSupport support = new TestSupport();
        Instant timestamp = Instant.parse("2026-06-05T10:00:00Z");

        ResultSet bucketRs = mock(ResultSet.class);
        when(bucketRs.getString("world")).thenReturn("Antica");
        when(bucketRs.getTimestamp("bucket_start")).thenReturn(Timestamp.from(timestamp));
        when(bucketRs.getInt("samples")).thenReturn(3);
        when(bucketRs.getDouble("average_players_online")).thenReturn(150.5);
        when(bucketRs.getInt("min_players_online")).thenReturn(100);
        when(bucketRs.getInt("max_players_online")).thenReturn(200);
        when(bucketRs.getInt("first_players_online")).thenReturn(120);
        when(bucketRs.getInt("last_players_online")).thenReturn(180);
        when(bucketRs.wasNull()).thenReturn(false);
        var bucket = support.mapBucket(bucketRs, 0);

        assertThat(bucket.world()).isEqualTo("Antica");
        assertThat(bucket.bucketStart()).isEqualTo(timestamp);
        assertThat(bucket.changePlayersOnline()).isEqualTo(60);

        ResultSet summaryRs = mock(ResultSet.class);
        when(summaryRs.getString("world")).thenReturn("Secura");
        when(summaryRs.getInt("samples")).thenReturn(4);
        when(summaryRs.getTimestamp("first_scrape_at")).thenReturn(Timestamp.from(timestamp));
        when(summaryRs.getTimestamp("last_scrape_at")).thenReturn(Timestamp.from(timestamp.plusSeconds(3600)));
        when(summaryRs.getInt("min_players_online")).thenReturn(10);
        when(summaryRs.getInt("peak_players_online")).thenReturn(40);
        when(summaryRs.getTimestamp("peak_at")).thenReturn(Timestamp.from(timestamp.plusSeconds(1800)));
        when(summaryRs.getDouble("average_players_online")).thenReturn(22.5);
        when(summaryRs.getInt("first_players_online")).thenReturn(12);
        when(summaryRs.getInt("latest_players_online")).thenReturn(30);
        when(summaryRs.wasNull()).thenReturn(false);
        var summary = support.mapSummary(summaryRs, 0);

        assertThat(summary.world()).isEqualTo("Secura");
        assertThat(summary.changePlayersOnline()).isEqualTo(18);

        ResultSet rankingRs = mock(ResultSet.class);
        when(rankingRs.getString("world")).thenReturn("Wintera");
        when(rankingRs.getString("metric")).thenReturn("peak");
        when(rankingRs.getDouble("metric_value")).thenReturn(99.0);
        when(rankingRs.getInt("samples")).thenReturn(2);
        when(rankingRs.getTimestamp("first_scrape_at")).thenReturn(Timestamp.from(timestamp));
        when(rankingRs.getTimestamp("last_scrape_at")).thenReturn(Timestamp.from(timestamp.plusSeconds(60)));
        when(rankingRs.getInt("peak_players_online")).thenReturn(99);
        when(rankingRs.getDouble("average_players_online")).thenReturn(88.0);
        when(rankingRs.getInt("first_players_online")).thenReturn(70);
        when(rankingRs.getInt("latest_players_online")).thenReturn(95);
        when(rankingRs.wasNull()).thenReturn(false);
        var ranking = support.mapRanking(rankingRs, 0);

        assertThat(ranking.world()).isEqualTo("Wintera");
        assertThat(ranking.metric()).isEqualTo("peak");
        assertThat(ranking.changePlayersOnline()).isEqualTo(25);
    }

    @Test
    void guildQueryViewsMapNullAndPresentNestedEntities() {
        Guild guild = new Guild();
        guild.setId(10L);
        guild.setName("Raw Raw");
        guild.setWorld(null);
        var guildView = GuildQueryViews.GuildView.from(guild);

        assertThat(guildView.id()).isEqualTo(10L);
        assertThat(guildView.world()).isNull();

        GuildMembership membership = new GuildMembership();
        membership.setId(20L);
        membership.setGuild(null);
        membership.setCharacter(null);
        membership.setCharacterNameSnapshot("Member Name");
        membership.setActive(false);
        var memberView = GuildQueryViews.GuildMemberView.from(membership);

        assertThat(memberView.guildId()).isNull();
        assertThat(memberView.characterId()).isNull();
        assertThat(memberView.characterName()).isEqualTo("Member Name");
        assertThat(memberView.active()).isFalse();

        CharacterEntity character = new CharacterEntity();
        character.setId(30L);
        Guild fromGuild = new Guild();
        fromGuild.setId(40L);
        fromGuild.setName("Old Guild");
        Guild toGuild = new Guild();
        toGuild.setId(50L);
        toGuild.setName("New Guild");
        GuildMembershipEvent event = new GuildMembershipEvent();
        event.setId(60L);
        event.setCharacter(character);
        event.setCharacterNameSnapshot("Transfer Name");
        event.setEventType(GuildMembershipEventType.TRANSFERRED);
        event.setFromGuild(fromGuild);
        event.setToGuild(toGuild);
        event.setObservedAt(Instant.parse("2026-06-05T12:00:00Z"));
        event.setDescription("Moved guilds");
        var eventView = GuildQueryViews.GuildMembershipEventView.from(event);

        assertThat(eventView.characterId()).isEqualTo(30L);
        assertThat(eventView.fromGuildName()).isEqualTo("Old Guild");
        assertThat(eventView.toGuildName()).isEqualTo("New Guild");

        GuildMembershipEvent nullEvent = new GuildMembershipEvent();
        nullEvent.setEventType(GuildMembershipEventType.LEFT);
        nullEvent.setCharacterNameSnapshot("Unknown");
        var nullEventView = GuildQueryViews.GuildMembershipEventView.from(nullEvent);

        assertThat(nullEventView.characterId()).isNull();
        assertThat(nullEventView.fromGuildId()).isNull();
        assertThat(nullEventView.toGuildId()).isNull();
    }

    @Test
    void worldOnlineSummaryEmptyFactoryKeepsRequestedWorldAndNullMetrics() {
        var empty = WorldOnlineAnalyticsService.WorldOnlineSummaryView.empty("Antica");

        assertThat(empty.world()).isEqualTo("Antica");
        assertThat(empty.samples()).isZero();
        assertThat(empty.peakPlayersOnline()).isNull();
        assertThat(empty.changePlayersOnline()).isNull();
    }

    private static final class TestSupport extends WorldOnlineAnalyticsJdbcSupport {
        private TestSupport() {
            super(mock(JdbcTemplate.class));
        }
    }
}
