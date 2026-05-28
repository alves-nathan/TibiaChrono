#!/usr/bin/env bash
set -euo pipefail

# Adds REST endpoints backed by the compact highscore storage model.

mkdir -p 'src/main/java/com/nathan/tibiastats/application/service'
cat > 'src/main/java/com/nathan/tibiastats/application/service/HighscoreApiQueryService.java' <<'JAVA'
package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class HighscoreApiQueryService {
    private final NamedParameterJdbcTemplate jdbc;

    public HighscoreApiQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public List<ExperienceDailyView> findExperienceDaily(String world,
                                                         LocalDate date,
                                                         Integer vocationFilterId,
                                                         int limit) {
        var params = new MapSqlParameterSource()
                .addValue("world", normalizeRequired(world, "world"))
                .addValue("date", date)
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("limit", safeLimit(limit));

        return jdbc.query("""
                with selected_date as (
                    select coalesce(:date, max(d.date)) as value
                    from highscore_exp_daily d
                    join worlds w on w.id = d.world_id
                    where lower(w.name) = lower(:world)
                )
                select
                    d.date,
                    r.rank,
                    active_name.name as character_name,
                    d.character_id,
                    w.name as world,
                    cast(:vocationFilterId as integer) as vocation_filter_id,
                    d.experience,
                    d.level,
                    d.first_seen_filter,
                    d.scraped_at
                from highscore_exp_daily d
                join worlds w on w.id = d.world_id
                join selected_date sd on sd.value = d.date
                left join highscore_exp_rank_daily r
                    on r.date = d.date
                   and r.character_id = d.character_id
                   and r.world_id = d.world_id
                   and r.vocation_filter_id = :vocationFilterId
                left join character_names active_name
                    on active_name.character_id = d.character_id
                   and active_name.active is true
                where lower(w.name) = lower(:world)
                order by r.rank nulls last, d.experience desc, active_name.name nulls last
                limit :limit
                """, params, this::mapExperienceDaily);
    }

    public List<ExperienceDailyView> findExperienceRanks(String world,
                                                         LocalDate date,
                                                         Integer vocationFilterId,
                                                         int limit) {
        var params = new MapSqlParameterSource()
                .addValue("world", normalizeRequired(world, "world"))
                .addValue("date", date)
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("limit", safeLimit(limit));

        return jdbc.query("""
                with selected_date as (
                    select coalesce(:date, max(r.date)) as value
                    from highscore_exp_rank_daily r
                    join worlds w on w.id = r.world_id
                    where lower(w.name) = lower(:world)
                      and r.vocation_filter_id = :vocationFilterId
                )
                select
                    r.date,
                    r.rank,
                    active_name.name as character_name,
                    r.character_id,
                    w.name as world,
                    r.vocation_filter_id,
                    d.experience,
                    d.level,
                    d.first_seen_filter,
                    greatest(r.scraped_at, d.scraped_at) as scraped_at
                from highscore_exp_rank_daily r
                join worlds w on w.id = r.world_id
                join selected_date sd on sd.value = r.date
                join highscore_exp_daily d
                    on d.date = r.date
                   and d.character_id = r.character_id
                   and d.world_id = r.world_id
                left join character_names active_name
                    on active_name.character_id = r.character_id
                   and active_name.active is true
                where lower(w.name) = lower(:world)
                  and r.vocation_filter_id = :vocationFilterId
                order by r.rank asc
                limit :limit
                """, params, this::mapExperienceDaily);
    }

    public List<ExperienceGainView> findExperienceGains(String world,
                                                        LocalDate startDate,
                                                        LocalDate endDate,
                                                        Integer vocationFilterId,
                                                        int limit) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be greater than or equal to startDate");
        }

        var params = new MapSqlParameterSource()
                .addValue("world", normalizeRequired(world, "world"))
                .addValue("startDate", startDate)
                .addValue("endDate", endDate)
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("limit", safeLimit(limit));

        return jdbc.query("""
                with candidates as (
                    select d.*
                    from highscore_exp_daily d
                    join worlds w on w.id = d.world_id
                    where lower(w.name) = lower(:world)
                      and d.date between :startDate and :endDate
                ),
                start_rows as (
                    select distinct on (character_id, world_id)
                        character_id, world_id, date, experience
                    from candidates
                    order by character_id, world_id, date asc
                ),
                end_rows as (
                    select distinct on (character_id, world_id)
                        character_id, world_id, date, experience
                    from candidates
                    order by character_id, world_id, date desc
                )
                select
                    active_name.name as character_name,
                    e.character_id,
                    w.name as world,
                    s.date as start_date,
                    e.date as end_date,
                    s.experience as start_experience,
                    e.experience as end_experience,
                    e.experience - s.experience as gain,
                    sr.rank as start_rank,
                    er.rank as end_rank,
                    cast(:vocationFilterId as integer) as vocation_filter_id
                from end_rows e
                join start_rows s
                  on s.character_id = e.character_id
                 and s.world_id = e.world_id
                join worlds w on w.id = e.world_id
                left join highscore_exp_rank_daily sr
                  on sr.date = s.date
                 and sr.character_id = s.character_id
                 and sr.world_id = s.world_id
                 and sr.vocation_filter_id = :vocationFilterId
                left join highscore_exp_rank_daily er
                  on er.date = e.date
                 and er.character_id = e.character_id
                 and er.world_id = e.world_id
                 and er.vocation_filter_id = :vocationFilterId
                left join character_names active_name
                  on active_name.character_id = e.character_id
                 and active_name.active is true
                where e.date > s.date
                  and e.experience >= s.experience
                  and er.rank is not null
                order by gain desc, end_experience desc, active_name.name nulls last
                limit :limit
                """, params, this::mapExperienceGain);
    }

    public List<CurrentHighscoreView> findCurrent(String world,
                                                  StatCategory category,
                                                  Integer vocationFilterId,
                                                  int limit) {
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (category == StatCategory.EXPERIENCE) {
            throw new IllegalArgumentException("Use /api/highscores/exp/* endpoints for EXPERIENCE");
        }

        var params = new MapSqlParameterSource()
                .addValue("world", normalizeRequired(world, "world"))
                .addValue("category", categoryCode(category))
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("limit", safeLimit(limit));

        return jdbc.query("""
                select
                    h.id,
                    h.rank,
                    active_name.name as character_name,
                    h.character_id,
                    w.name as world,
                    h.category,
                    h.vocation_filter_id,
                    h.value,
                    h.first_seen_date,
                    h.last_seen_date,
                    h.last_changed_date,
                    h.scraped_at
                from highscore_current_records h
                join worlds w on w.id = h.world_id
                left join character_names active_name
                  on active_name.character_id = h.character_id
                 and active_name.active is true
                where lower(w.name) = lower(:world)
                  and h.category = :category
                  and h.vocation_filter_id = :vocationFilterId
                order by h.rank asc
                limit :limit
                """, params, this::mapCurrentHighscore);
    }

    public List<PeriodHighscoreView> findHistory(String world,
                                                 StatCategory category,
                                                 String characterName,
                                                 Integer vocationFilterId,
                                                 LocalDate from,
                                                 LocalDate to,
                                                 int limit) {
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (category == StatCategory.EXPERIENCE) {
            throw new IllegalArgumentException("Use /api/highscores/exp/daily for EXPERIENCE history");
        }

        var sql = new StringBuilder("""
                select
                    p.id,
                    p.rank,
                    active_name.name as character_name,
                    p.character_id,
                    w.name as world,
                    p.category,
                    p.vocation_filter_id,
                    p.value,
                    p.valid_from,
                    p.valid_until,
                    p.created_at
                from highscore_record_periods p
                join worlds w on w.id = p.world_id
                left join character_names active_name
                  on active_name.character_id = p.character_id
                 and active_name.active is true
                where lower(w.name) = lower(:world)
                  and p.category = :category
                  and p.vocation_filter_id = :vocationFilterId
                """);
        var params = new MapSqlParameterSource()
                .addValue("world", normalizeRequired(world, "world"))
                .addValue("category", categoryCode(category))
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("fromDate", from)
                .addValue("toDate", to)
                .addValue("characterName", characterName == null ? null : characterName.trim())
                .addValue("limit", safeLimit(limit));

        if (from != null) {
            sql.append(" and coalesce(p.valid_until, date '9999-12-31') >= :fromDate");
        }
        if (to != null) {
            sql.append(" and p.valid_from <= :toDate");
        }
        if (characterName != null && !characterName.isBlank()) {
            sql.append("""
                    and p.character_id = (
                        select lookup.character_id
                        from character_names lookup
                        where lower(lookup.name) = lower(:characterName)
                          and (
                              lookup.active is true
                              or lookup.inactive_date >= now() - interval '6 months'
                          )
                        order by lookup.active desc, lookup.inactive_date desc nulls last
                        limit 1
                    )
                    """);
        }
        sql.append(" order by p.valid_from desc, p.rank asc limit :limit");
        return jdbc.query(sql.toString(), params, this::mapPeriodHighscore);
    }

    public List<ExperienceDailyView> findCharacterExperienceDaily(String characterName,
                                                                  String world,
                                                                  Integer vocationFilterId,
                                                                  LocalDate from,
                                                                  LocalDate to,
                                                                  int limit) {
        var sql = new StringBuilder("""
                with resolved as (
                    select lookup.character_id
                    from character_names lookup
                    where lower(lookup.name) = lower(:characterName)
                      and (
                          lookup.active is true
                          or lookup.inactive_date >= now() - interval '6 months'
                      )
                    order by lookup.active desc, lookup.inactive_date desc nulls last
                    limit 1
                )
                select
                    d.date,
                    r.rank,
                    active_name.name as character_name,
                    d.character_id,
                    w.name as world,
                    cast(:vocationFilterId as integer) as vocation_filter_id,
                    d.experience,
                    d.level,
                    d.first_seen_filter,
                    d.scraped_at
                from highscore_exp_daily d
                join resolved resolved on resolved.character_id = d.character_id
                join worlds w on w.id = d.world_id
                left join highscore_exp_rank_daily r
                    on r.date = d.date
                   and r.character_id = d.character_id
                   and r.world_id = d.world_id
                   and r.vocation_filter_id = :vocationFilterId
                left join character_names active_name
                    on active_name.character_id = d.character_id
                   and active_name.active is true
                where 1 = 1
                """);
        var params = new MapSqlParameterSource()
                .addValue("characterName", normalizeRequired(characterName, "characterName"))
                .addValue("world", world == null ? null : world.trim())
                .addValue("vocationFilterId", vocationFilterId == null ? 0 : vocationFilterId)
                .addValue("fromDate", from)
                .addValue("toDate", to)
                .addValue("limit", safeLimit(limit));
        if (world != null && !world.isBlank()) {
            sql.append(" and lower(w.name) = lower(:world)");
        }
        if (from != null) {
            sql.append(" and d.date >= :fromDate");
        }
        if (to != null) {
            sql.append(" and d.date <= :toDate");
        }
        sql.append(" order by d.date desc, w.name limit :limit");
        return jdbc.query(sql.toString(), params, this::mapExperienceDaily);
    }

    private ExperienceDailyView mapExperienceDaily(ResultSet rs, int rowNum) throws SQLException {
        return new ExperienceDailyView(
                rs.getObject("date", LocalDate.class),
                getNullableInteger(rs, "rank"),
                rs.getString("character_name"),
                rs.getLong("character_id"),
                rs.getString("world"),
                getNullableInteger(rs, "vocation_filter_id"),
                rs.getLong("experience"),
                getNullableInteger(rs, "level"),
                getNullableInteger(rs, "first_seen_filter"),
                toInstant(rs.getTimestamp("scraped_at"))
        );
    }

    private ExperienceGainView mapExperienceGain(ResultSet rs, int rowNum) throws SQLException {
        return new ExperienceGainView(
                rs.getString("character_name"),
                rs.getLong("character_id"),
                rs.getString("world"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                rs.getLong("start_experience"),
                rs.getLong("end_experience"),
                rs.getLong("gain"),
                getNullableInteger(rs, "start_rank"),
                getNullableInteger(rs, "end_rank"),
                getNullableInteger(rs, "vocation_filter_id")
        );
    }

    private CurrentHighscoreView mapCurrentHighscore(ResultSet rs, int rowNum) throws SQLException {
        int categoryCode = rs.getInt("category");
        return new CurrentHighscoreView(
                rs.getLong("id"),
                rs.getInt("rank"),
                rs.getString("character_name"),
                rs.getLong("character_id"),
                rs.getString("world"),
                categoryName(categoryCode),
                categoryCode,
                getNullableInteger(rs, "vocation_filter_id"),
                rs.getLong("value"),
                rs.getObject("first_seen_date", LocalDate.class),
                rs.getObject("last_seen_date", LocalDate.class),
                rs.getObject("last_changed_date", LocalDate.class),
                toInstant(rs.getTimestamp("scraped_at"))
        );
    }

    private PeriodHighscoreView mapPeriodHighscore(ResultSet rs, int rowNum) throws SQLException {
        int categoryCode = rs.getInt("category");
        return new PeriodHighscoreView(
                rs.getLong("id"),
                rs.getInt("rank"),
                rs.getString("character_name"),
                rs.getLong("character_id"),
                rs.getString("world"),
                categoryName(categoryCode),
                categoryCode,
                getNullableInteger(rs, "vocation_filter_id"),
                rs.getLong("value"),
                rs.getObject("valid_from", LocalDate.class),
                rs.getObject("valid_until", LocalDate.class),
                toInstant(rs.getTimestamp("created_at"))
        );
    }

    private String normalizeRequired(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private int safeLimit(int requested) {
        if (requested <= 0) {
            return 100;
        }
        return Math.min(requested, 1000);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private short categoryCode(StatCategory category) {
        return switch (category) {
            case ACHIEVEMENTS -> 1;
            case AXE_FIGHTING -> 2;
            case BOSS_POINTS -> 15;
            case BOUNTY_POINTS_EARNED -> 16;
            case CHARM_POINTS -> 3;
            case CLUB_FIGHTING -> 4;
            case DISTANCE_FIGHTING -> 5;
            case DROME_SCORE -> 14;
            case EXPERIENCE -> 6;
            case FISHING -> 7;
            case FIST_FIGHTING -> 8;
            case GOSHNARS_TAINT -> 9;
            case LOYALTY_POINTS -> 10;
            case MAGIC_LEVEL -> 11;
            case SHIELDING -> 12;
            case SWORD_FIGHTING -> 13;
            case WEEKLY_TASKS_COMPLETED -> 17;
        };
    }

    private String categoryName(int code) {
        return switch (code) {
            case 1 -> StatCategory.ACHIEVEMENTS.name();
            case 2 -> StatCategory.AXE_FIGHTING.name();
            case 3 -> StatCategory.CHARM_POINTS.name();
            case 4 -> StatCategory.CLUB_FIGHTING.name();
            case 5 -> StatCategory.DISTANCE_FIGHTING.name();
            case 6 -> StatCategory.EXPERIENCE.name();
            case 7 -> StatCategory.FISHING.name();
            case 8 -> StatCategory.FIST_FIGHTING.name();
            case 9 -> StatCategory.GOSHNARS_TAINT.name();
            case 10 -> StatCategory.LOYALTY_POINTS.name();
            case 11 -> StatCategory.MAGIC_LEVEL.name();
            case 12 -> StatCategory.SHIELDING.name();
            case 13 -> StatCategory.SWORD_FIGHTING.name();
            case 14 -> StatCategory.DROME_SCORE.name();
            case 15 -> StatCategory.BOSS_POINTS.name();
            case 16 -> StatCategory.BOUNTY_POINTS_EARNED.name();
            case 17 -> StatCategory.WEEKLY_TASKS_COMPLETED.name();
            default -> "UNKNOWN";
        };
    }

    public record ExperienceDailyView(
            LocalDate date,
            Integer rank,
            String characterName,
            Long characterId,
            String world,
            Integer vocationFilterId,
            Long experience,
            Integer level,
            Integer firstSeenFilter,
            Instant scrapedAt
    ) {}

    public record ExperienceGainView(
            String characterName,
            Long characterId,
            String world,
            LocalDate startDate,
            LocalDate endDate,
            Long startExperience,
            Long endExperience,
            Long gain,
            Integer startRank,
            Integer endRank,
            Integer vocationFilterId
    ) {}

    public record CurrentHighscoreView(
            Long id,
            Integer rank,
            String characterName,
            Long characterId,
            String world,
            String category,
            Integer categoryId,
            Integer vocationFilterId,
            Long value,
            LocalDate firstSeenDate,
            LocalDate lastSeenDate,
            LocalDate lastChangedDate,
            Instant scrapedAt
    ) {}

    public record PeriodHighscoreView(
            Long id,
            Integer rank,
            String characterName,
            Long characterId,
            String world,
            String category,
            Integer categoryId,
            Integer vocationFilterId,
            Long value,
            LocalDate validFrom,
            LocalDate validUntil,
            Instant createdAt
    ) {}
}
JAVA

mkdir -p 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest'
cat > 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/HighscoreController.java' <<'JAVA'
package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.HighscoreApiQueryService;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/highscores")
public class HighscoreController {
    private final HighscoreApiQueryService highscores;

    public HighscoreController(HighscoreApiQueryService highscores) {
        this.highscores = highscores;
    }

    /**
     * Backward-compatible endpoint.
     *
     * EXPERIENCE reads from the compact EXP rank/daily tables.
     * Non-EXP categories read from highscore_current_records.
     */
    @GetMapping
    public List<?> getHighscores(
            @RequestParam String world,
            @RequestParam StatCategory category,
            @RequestParam(required = false) Integer vocationFilterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            if (category == StatCategory.EXPERIENCE) {
                return highscores.findExperienceRanks(world, date, vocationFilterId, limit);
            }
            return highscores.findCurrent(world, category, vocationFilterId, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/exp/daily")
    public List<HighscoreApiQueryService.ExperienceDailyView> getExperienceDaily(
            @RequestParam String world,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return highscores.findExperienceDaily(world, date, vocationFilterId, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/exp/ranks")
    public List<HighscoreApiQueryService.ExperienceDailyView> getExperienceRanks(
            @RequestParam String world,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return highscores.findExperienceRanks(world, date, vocationFilterId, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/exp/gains")
    public List<HighscoreApiQueryService.ExperienceGainView> getExperienceGains(
            @RequestParam String world,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return highscores.findExperienceGains(world, startDate, endDate, vocationFilterId, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/current")
    public List<HighscoreApiQueryService.CurrentHighscoreView> getCurrent(
            @RequestParam String world,
            @RequestParam StatCategory category,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return highscores.findCurrent(world, category, vocationFilterId, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/history")
    public List<HighscoreApiQueryService.PeriodHighscoreView> getHistory(
            @RequestParam String world,
            @RequestParam StatCategory category,
            @RequestParam(required = false) String characterName,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return highscores.findHistory(world, category, characterName, vocationFilterId, from, to, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
JAVA

mkdir -p 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest'
cat > 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/CharacterController.java' <<'JAVA'
package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.ApiQueryService;
import com.nathan.tibiastats.application.service.HighscoreApiQueryService;
import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {
    private final ApiQueryService queries;
    private final HighscoreApiQueryService highscores;

    public CharacterController(ApiQueryService queries, HighscoreApiQueryService highscores) {
        this.queries = queries;
        this.highscores = highscores;
    }

    @GetMapping("/{name}")
    public ApiQueryService.CharacterView getCharacter(@PathVariable String name) {
        return queries.findCharacter(name).orElseThrow();
    }

    @GetMapping("/{name}/names")
    public List<ApiQueryService.CharacterNameView> getCharacterNames(@PathVariable String name) {
        return queries.findCharacterNames(name);
    }

    @GetMapping("/{name}/highscores")
    public List<?> getCharacterHighscores(
            @PathVariable String name,
            @RequestParam(required = false) StatCategory category,
            @RequestParam(required = false) String world,
            @RequestParam(defaultValue = "0") Integer vocationFilterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            if (category == null || category == StatCategory.EXPERIENCE) {
                return highscores.findCharacterExperienceDaily(name, world, vocationFilterId, from, to, limit);
            }
            if (world == null || world.isBlank()) {
                throw new IllegalArgumentException("world is required for non-EXPERIENCE character highscore history");
            }
            return highscores.findHistory(world, category, name, vocationFilterId, from, to, limit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
JAVA

mkdir -p 'src/test/java/com/nathan/tibiastats/highscore'
cat > 'src/test/java/com/nathan/tibiastats/highscore/HighscoreCompactRestApiIntegrationTest.java' <<'JAVA'
package com.nathan.tibiastats.highscore;

import com.jayway.jsonpath.JsonPath;
import com.nathan.tibiastats.AbstractPostgresTest;
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

    private HighscoreStatRecordWriter.HighscoreStatRow row(Long characterId,
                                                           Integer worldId,
                                                           StatCategory category,
                                                           int vocationFilterId,
                                                           LocalDate date,
                                                           long value,
                                                           int rank) {
        return new HighscoreStatRecordWriter.HighscoreStatRow(
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
JAVA

echo "Highscore compact REST API files updated."
