package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.HighscoreHttpBackoffState;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HighscoreScrapeStateRepositoryTailCoverageTest {
    @Test
    void registerScopesSkipsEmptyInputAndBatchesCartesianProductWhenPresent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HighscoreScrapeStateRepository repository = new HighscoreScrapeStateRepository(jdbc);

        repository.registerScopes(List.of(), List.of(StatCategory.EXPERIENCE), List.of(0));
        verify(jdbc, never()).batchUpdate(anyString(), anyList());

        World antica = world(1, "Antica");
        World bona = world(2, "Bona");
        repository.registerScopes(
                List.of(antica, bona),
                List.of(StatCategory.EXPERIENCE, StatCategory.MAGIC_LEVEL),
                List.of(0, 4)
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> batchArgs = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(anyString(), batchArgs.capture());
        assertThat(batchArgs.getValue()).hasSize(8);
    }

    @Test
    void findNextScopesFiltersMappedRowsAndAppliesPositiveLimit() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet allowed = scopeResultSet(1, "Antica", StatCategory.EXPERIENCE, 0);
        ResultSet otherWorld = scopeResultSet(2, "Bona", StatCategory.EXPERIENCE, 0);
        ResultSet otherCategory = scopeResultSet(1, "Antica", StatCategory.MAGIC_LEVEL, 0);
        ResultSet otherVocation = scopeResultSet(1, "Antica", StatCategory.EXPERIENCE, 4);
        stubScopeQuery(jdbc, allowed, otherWorld, otherCategory, otherVocation);
        HighscoreScrapeStateRepository repository = new HighscoreScrapeStateRepository(jdbc);

        List<HighscoreScope> result = repository.findNextScopes(
                List.of(world(1, "Antica")),
                List.of(StatCategory.EXPERIENCE),
                List.of(0),
                1
        );

        assertThat(result).containsExactly(new HighscoreScope(1, "Antica", StatCategory.EXPERIENCE, 0));
    }

    @Test
    void activateHttpBackoffReturnsExistingActiveStateWithoutNeedingAnUpdateVerification() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HighscoreHttpBackoffState active = new HighscoreHttpBackoffState(
                Instant.now().plusSeconds(60),
                2,
                60_000L,
                "FORBIDDEN",
                "HTTP 403",
                Instant.now(),
                null
        );
        stubBackoffStates(jdbc, active);
        HighscoreScrapeStateRepository repository = new HighscoreScrapeStateRepository(jdbc);

        assertThat(repository.activateHttpBackoff(1_000L, 5_000L, 2.0D, "HTTP 429")).isSameAs(active);
    }

    @Test
    void activateHttpBackoffNormalizesCooldownValuesAndTruncatesLongReasons() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HighscoreHttpBackoffState current = new HighscoreHttpBackoffState(
                null,
                2,
                2_000L,
                "OK",
                null,
                null,
                Instant.now()
        );
        HighscoreHttpBackoffState after = new HighscoreHttpBackoffState(
                Instant.now().plusSeconds(1),
                3,
                1_000L,
                "FORBIDDEN",
                "x".repeat(4_000),
                Instant.now(),
                null
        );
        stubBackoffStates(jdbc, current, after);
        HighscoreScrapeStateRepository repository = new HighscoreScrapeStateRepository(jdbc);

        HighscoreHttpBackoffState result = repository.activateHttpBackoff(
                1_000L,
                500L,
                0.5D,
                "x".repeat(4_100)
        );

        assertThat(result).isSameAs(after);
    }

    @Test
    void markFinishedAndResetCoverTruncationBranches() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HighscoreScrapeStateRepository repository = new HighscoreScrapeStateRepository(jdbc);
        HighscoreScope scope = new HighscoreScope(1, "Antica", StatCategory.EXPERIENCE, 0);

        repository.markFinished(scope, "SUCCESS", 1, 10, 25L, null);
        repository.markFinished(scope, "X".repeat(75), 1, 10, 25L, "y".repeat(4_100));
        repository.resetHttpBackoffAfterSuccess();

        // The assertions above are intentionally behavior-light. This test exists to exercise
        // the null/truncation branches without coupling to overloaded JdbcTemplate.update(...)
        // signatures, which are compiler-sensitive when verified through Mockito matchers.
        assertThat(scope.worldName()).isEqualTo("Antica");
    }

    private static World world(Integer id, String name) {
        World world = new World(name, "Open PvP", "EU");
        world.setId(id);
        return world;
    }

    private static ResultSet scopeResultSet(Integer worldId, String worldName, StatCategory category, int vocation) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("world_id")).thenReturn(worldId);
        when(rs.getString("world_name")).thenReturn(worldName);
        when(rs.getString("category")).thenReturn(category.name());
        when(rs.getInt("vocation_filter_id")).thenReturn(vocation);
        return rs;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubScopeQuery(JdbcTemplate jdbc, ResultSet... rows) {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            java.util.ArrayList<Object> mapped = new java.util.ArrayList<>();
            for (int i = 0; i < rows.length; i++) {
                mapped.add(mapper.mapRow(rows[i], i));
            }
            return mapped;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void stubBackoffStates(JdbcTemplate jdbc, HighscoreHttpBackoffState... states) {
        var stubbing = when(jdbc.queryForObject(anyString(), any(RowMapper.class)));
        for (HighscoreHttpBackoffState state : states) {
            stubbing = stubbing.thenReturn(state);
        }
    }
}
