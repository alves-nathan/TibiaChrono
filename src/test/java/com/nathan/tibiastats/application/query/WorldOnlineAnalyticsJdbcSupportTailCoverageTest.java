package com.nathan.tibiastats.application.query;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WorldOnlineAnalyticsJdbcSupportTailCoverageTest {
    @Test
    void requireWorldRejectsBlankWorldName() {
        TestSupport support = new TestSupport();

        assertThatThrownBy(() -> support.requireWorld(" "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("world is required");
    }

    @Test
    void parseWorldsRejectsBlankEmptyAndTooManyWorlds() {
        TestSupport support = new TestSupport();

        assertThatThrownBy(() -> support.parseWorlds(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("worlds is required");

        assertThatThrownBy(() -> support.parseWorlds(", , "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("worlds must contain at least one world name");

        String tooManyWorlds = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "World" + i)
                .collect(Collectors.joining(","));

        assertThatThrownBy(() -> support.parseWorlds(tooManyWorlds))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at most 20");
    }

    @Test
    void onlineBucketDefaultsAndRejectsUnsupportedValues() {
        assertThat(WorldOnlineAnalyticsJdbcSupport.OnlineBucket.from(null))
                .isEqualTo(WorldOnlineAnalyticsJdbcSupport.OnlineBucket.HOUR);

        assertThatThrownBy(() -> WorldOnlineAnalyticsJdbcSupport.OnlineBucket.from("week"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported bucket");
    }

    @Test
    void onlineRankingMetricDefaultsNormalizesAndRejectsUnsupportedValues() {
        assertThat(WorldOnlineAnalyticsJdbcSupport.OnlineRankingMetric.from("latest"))
                .isEqualTo(WorldOnlineAnalyticsJdbcSupport.OnlineRankingMetric.LATEST);
        assertThat(WorldOnlineAnalyticsJdbcSupport.OnlineRankingMetric.from(null))
                .isEqualTo(WorldOnlineAnalyticsJdbcSupport.OnlineRankingMetric.PEAK);

        assertThatThrownBy(() -> WorldOnlineAnalyticsJdbcSupport.OnlineRankingMetric.from("invalid-metric"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported metric");
    }

    private static final class TestSupport extends WorldOnlineAnalyticsJdbcSupport {
        private TestSupport() {
            super(mock(JdbcTemplate.class));
        }
    }
}
