package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class ScrapeJobReadModelService extends JdbcReadModelSupport {

    public ScrapeJobReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<ApiQueryService.ScrapeJobView> findScrapeJobs(String jobName, String status, int limit) {
        var sql = new StringBuilder("""
                select
                    id,
                    job_name,
                    status,
                    started_at,
                    finished_at,
                    duration_ms,
                    items_processed,
                    items_created,
                    items_updated,
                    items_failed,
                    error_message
                from scrape_jobs
                where 1 = 1
                """);
        var params = new MapSqlParameterSource("limit", safeLimit(limit));
        if (jobName != null && !jobName.isBlank()) {
            sql.append(" and job_name = :jobName");
            params.addValue("jobName", jobName.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }
        sql.append(" order by started_at desc limit :limit");
        return jdbc.query(sql.toString(), prepareParams(params), this::mapScrapeJob);
    }

    private ApiQueryService.ScrapeJobView mapScrapeJob(ResultSet rs, int rowNum) throws SQLException {
        return new ApiQueryService.ScrapeJobView(
                rs.getLong("id"),
                rs.getString("job_name"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("finished_at")),
                getNullableLong(rs, "duration_ms"),
                getNullableInteger(rs, "items_processed"),
                getNullableInteger(rs, "items_created"),
                getNullableInteger(rs, "items_updated"),
                getNullableInteger(rs, "items_failed"),
                rs.getString("error_message")
        );
    }
}
