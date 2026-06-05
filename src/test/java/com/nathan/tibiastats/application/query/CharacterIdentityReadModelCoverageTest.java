package com.nathan.tibiastats.application.query;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterIdentityReadModelCoverageTest {
    private static final Instant NOW = Instant.parse("2026-06-05T12:00:00Z");
    private static final OffsetDateTime LAST_LOGIN = OffsetDateTime.parse("2026-06-04T20:00:00Z");

    @Test
    void findCharacterMapsCharacterDetailsAndNullableIntegerFields() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CharacterIdentityReadModelService service = new CharacterIdentityReadModelService(jdbcTemplate);
        stubSingleRowQuery(jdbcTemplate, characterResultSet());

        Optional<ApiQueryService.CharacterView> result = service.findCharacter("Knight One");

        assertThat(result).isPresent();
        ApiQueryService.CharacterView character = result.get();
        assertThat(character.id()).isEqualTo(10L);
        assertThat(character.activeName()).isEqualTo("Knight One");
        assertThat(character.level()).isEqualTo(300);
        assertThat(character.sex()).isEqualTo("male");
        assertThat(character.vocation()).isEqualTo("Knight");
        assertThat(character.vocationPromotionName()).isEqualTo("Elite Knight");
        assertThat(character.achievementPoints()).isNull();
        assertThat(character.residence()).isEqualTo("Thais");
        assertThat(character.lastLogin()).isEqualTo(LAST_LOGIN);
        assertThat(character.accStatus()).isEqualTo("Premium Account");
        assertThat(character.creationDate()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(character.detailsLastScrapedAt()).isEqualTo(NOW);
        assertThat(character.detailsLastScrapeStatus()).isEqualTo("UPDATED");
    }

    @Test
    void findCharacterNamesByNameReturnsEmptyWhenCharacterCannotBeResolved() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CharacterIdentityReadModelService service = new CharacterIdentityReadModelService(jdbcTemplate);
        stubEmptyQuery(jdbcTemplate);

        assertThat(service.findCharacterNames("Missing")).isEmpty();
    }

    @Test
    void findCharacterNamesByNameResolvesCharacterThenMapsNameRows() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CharacterIdentityReadModelService service = new CharacterIdentityReadModelService(jdbcTemplate);
        stubSequentialSingleRowQueries(jdbcTemplate, characterResultSet(), nameResultSet(false));

        List<ApiQueryService.CharacterNameView> result = service.findCharacterNames("Knight One");

        assertThat(result).singleElement().satisfies(name -> {
            assertThat(name.id()).isEqualTo(100L);
            assertThat(name.characterId()).isEqualTo(10L);
            assertThat(name.name()).isEqualTo("Old Knight");
            assertThat(name.active()).isFalse();
            assertThat(name.inactiveDate()).isEqualTo(NOW.minusSeconds(60));
        });
    }

    @Test
    void findCharacterNamesByIdMapsActiveNameWithoutInactiveDate() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CharacterIdentityReadModelService service = new CharacterIdentityReadModelService(jdbcTemplate);
        stubSingleRowQuery(jdbcTemplate, nameResultSet(true));

        List<ApiQueryService.CharacterNameView> result = service.findCharacterNames(10L);

        assertThat(result).singleElement().satisfies(name -> {
            assertThat(name.active()).isTrue();
            assertThat(name.inactiveDate()).isNull();
        });
    }

    private static ResultSet characterResultSet() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(10L);
        when(rs.getString("active_name")).thenReturn("Knight One");
        when(rs.getInt("level")).thenReturn(300);
        when(rs.getString("sex")).thenReturn("male");
        when(rs.getString("vocation")).thenReturn("Knight");
        when(rs.getString("vocation_promotion_name")).thenReturn("Elite Knight");
        when(rs.getInt("achievement_points")).thenReturn(0);
        when(rs.getString("residence")).thenReturn("Thais");
        when(rs.getObject("last_login", OffsetDateTime.class)).thenReturn(LAST_LOGIN);
        when(rs.getString("acc_status")).thenReturn("Premium Account");
        when(rs.getTimestamp("creation_date")).thenReturn(Timestamp.from(NOW.minusSeconds(3600)));
        when(rs.getTimestamp("details_last_scraped_at")).thenReturn(Timestamp.from(NOW));
        when(rs.getString("details_last_scrape_status")).thenReturn("UPDATED");
        when(rs.wasNull()).thenReturn(false, true);
        return rs;
    }

    private static ResultSet nameResultSet(boolean active) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(100L);
        when(rs.getLong("character_id")).thenReturn(10L);
        when(rs.getString("name")).thenReturn(active ? "Knight One" : "Old Knight");
        when(rs.getBoolean("active")).thenReturn(active);
        when(rs.getTimestamp("inactive_date")).thenReturn(active ? null : Timestamp.from(NOW.minusSeconds(60)));
        return rs;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubSingleRowQuery(JdbcTemplate jdbcTemplate, ResultSet rs) {
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubSequentialSingleRowQueries(JdbcTemplate jdbcTemplate, ResultSet first, ResultSet second) {
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(first, 0));
                })
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(second, 0));
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubEmptyQuery(JdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenReturn(List.of());
    }
}
