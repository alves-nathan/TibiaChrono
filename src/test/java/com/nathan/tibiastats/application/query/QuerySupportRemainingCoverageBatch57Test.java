package com.nathan.tibiastats.application.query;

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

class QuerySupportRemainingCoverageBatch57Test {
    @Test
    void jdbcReadModelSupportCoversNullParamsInstantConversionNullLimitAndNullableBoolean() throws Exception {
        TestJdbcSupport support = new TestJdbcSupport();
        Instant now = Instant.parse("2026-06-05T12:00:00Z");

        assertThat(support.prepare(null).getValues()).isEmpty();

        MapSqlParameterSource params = new MapSqlParameterSource("scrapedAt", now);
        assertThat(support.prepare(params).getValue("scrapedAt")).isEqualTo(Timestamp.from(now));
        assertThat(support.safeNullableLimit(null, 50, 500)).isEqualTo(50);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getBoolean("active")).thenReturn(false);
        when(rs.wasNull()).thenReturn(true);

        assertThat(support.nullableBoolean(rs, "active")).isNull();
    }

    @Test
    void worldOnlineEnumsCoverNullDefaultsAndInvalidValues() {
        assertThat(WorldOnlineAnalyticsJdbcSupport.OnlineBucket.from(null))
                .isEqualTo(WorldOnlineAnalyticsJdbcSupport.OnlineBucket.HOUR);
        assertThat(WorldOnlineAnalyticsJdbcSupport.OnlineRankingMetric.from(null))
                .isEqualTo(WorldOnlineAnalyticsJdbcSupport.OnlineRankingMetric.PEAK);

        assertThatThrownBy(() -> WorldOnlineAnalyticsJdbcSupport.OnlineBucket.from("week"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported bucket");
        assertThatThrownBy(() -> WorldOnlineAnalyticsJdbcSupport.OnlineRankingMetric.from("invalid-metric"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported metric");
    }

    private static final class TestJdbcSupport extends JdbcReadModelSupport {
        private TestJdbcSupport() {
            super(mock(JdbcTemplate.class));
        }

        private MapSqlParameterSource prepare(MapSqlParameterSource params) {
            return prepareParams(params);
        }

        private int safeNullableLimit(Integer requested, int defaultLimit, int maxLimit) {
            return safeLimit(requested, defaultLimit, maxLimit);
        }

        private Boolean nullableBoolean(ResultSet rs, String column) throws Exception {
            return getNullableBoolean(rs, column);
        }
    }
}
