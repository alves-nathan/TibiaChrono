package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.StatCategory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class CharacterTimelineHighscoreReadModelService extends CharacterTimelineJdbcSupport {
    private static final int DEFAULT_LIMIT = 200;

    public CharacterTimelineHighscoreReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<CharacterTimelineService.CharacterTimelineEvent> experienceTimeline(String characterName, Instant from, Instant to, int limit) {
        var params = baseParams(characterName, from, to, limit, DEFAULT_LIMIT);
        return jdbc.query("""
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
                    r.character_id,
                    active_name.name as character_name,
                    'HIGHSCORE_EXPERIENCE' as event_type,
                    (d.date::timestamp at time zone 'UTC') as occurred_at,
                    w.name as world,
                    cast(null as text) as guild_name,
                    'EXPERIENCE' as category,
                    rank_daily.rank,
                    d.experience as value,
                    cast(d.level as text) as title,
                    'Daily experience highscore snapshot' as description
                from resolved r
                join highscore_exp_daily d on d.character_id = r.character_id
                join worlds w on w.id = d.world_id
                left join highscore_exp_rank_daily rank_daily
                    on rank_daily.date = d.date
                   and rank_daily.character_id = d.character_id
                   and rank_daily.world_id = d.world_id
                   and rank_daily.vocation_filter_id = 0
                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
                where (cast(:fromInstant as timestamp with time zone) is null or (d.date::timestamp at time zone 'UTC') >= :fromInstant)
                  and (cast(:toInstant as timestamp with time zone) is null or (d.date::timestamp at time zone 'UTC') <= :toInstant)
                order by d.date desc, w.name asc
                limit :limit
                """, prepareParams(params), this::mapTimelineEvent);
    }

    public List<CharacterTimelineService.CharacterTimelineEvent> nonExperienceHighscoreTimeline(String characterName, Instant from, Instant to, int limit) {
        var params = baseParams(characterName, from, to, limit, DEFAULT_LIMIT);
        return jdbc.query("""
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
                    r.character_id,
                    active_name.name as character_name,
                    'HIGHSCORE_PERIOD_STARTED' as event_type,
                    (p.valid_from::timestamp at time zone 'UTC') as occurred_at,
                    w.name as world,
                    cast(null as text) as guild_name,
                    cast(p.category as text) as category,
                    p.rank,
                    p.value,
                    cast(null as text) as title,
                    'Non-experience highscore period started' as description
                from resolved r
                join highscore_record_periods p on p.character_id = r.character_id
                join worlds w on w.id = p.world_id
                left join character_names active_name on active_name.character_id = r.character_id and active_name.active is true
                where (cast(:fromInstant as timestamp with time zone) is null or (p.valid_from::timestamp at time zone 'UTC') >= :fromInstant)
                  and (cast(:toInstant as timestamp with time zone) is null or (p.valid_from::timestamp at time zone 'UTC') <= :toInstant)
                order by p.valid_from desc, w.name asc, p.category asc
                limit :limit
                """, prepareParams(params), (rs, rowNum) -> {
                    CharacterTimelineService.CharacterTimelineEvent event = mapTimelineEvent(rs, rowNum);
                    return new CharacterTimelineService.CharacterTimelineEvent(
                            event.characterId(),
                            event.characterName(),
                            event.eventType(),
                            event.occurredAt(),
                            event.world(),
                            event.guildName(),
                            categoryName(Integer.parseInt(event.category())),
                            event.rank(),
                            event.value(),
                            event.title(),
                            event.description(),
                            event.metadata()
                    );
                });
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
}
