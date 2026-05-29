package com.nathan.tibiastats.application.query;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

@ReadModelService
@ReadModelComponent
public class CharacterTimelineCoreReadModelService extends CharacterTimelineJdbcSupport {
    private static final int DEFAULT_LIMIT = 200;

    public CharacterTimelineCoreReadModelService(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public List<CharacterTimelineService.CharacterTimelineEvent> coreTimeline(String characterName, Instant from, Instant to, int limit) {
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
                ), active_name as (
                    select cn.character_id, cn.name
                    from character_names cn
                    join resolved r on r.character_id = cn.character_id
                    where cn.active is true
                    limit 1
                )
                select *
                from (
                    select
                        r.character_id,
                        an.name as character_name,
                        'CHARACTER_CREATED' as event_type,
                        c.creation_date as occurred_at,
                        cast(null as text) as world,
                        cast(null as text) as guild_name,
                        cast(null as text) as category,
                        cast(null as integer) as rank,
                        cast(null as bigint) as value,
                        cast(null as text) as title,
                        'Character creation date from Tibia character details' as description
                    from resolved r
                    join characters c on c.id = r.character_id
                    left join active_name an on an.character_id = r.character_id
                    where c.creation_date is not null

                    union all

                    select
                        r.character_id,
                        an.name as character_name,
                        'LAST_LOGIN' as event_type,
                        c.last_login as occurred_at,
                        cast(null as text) as world,
                        cast(null as text) as guild_name,
                        cast(null as text) as category,
                        cast(null as integer) as rank,
                        cast(null as bigint) as value,
                        cast(null as text) as title,
                        'Last login date from Tibia character details' as description
                    from resolved r
                    join characters c on c.id = r.character_id
                    left join active_name an on an.character_id = r.character_id
                    where c.last_login is not null

                    union all

                    select
                        r.character_id,
                        an.name as character_name,
                        'CHARACTER_DETAILS_SCRAPED' as event_type,
                        c.details_last_scraped_at as occurred_at,
                        cast(null as text) as world,
                        cast(null as text) as guild_name,
                        cast(null as text) as category,
                        cast(null as integer) as rank,
                        cast(null as bigint) as value,
                        c.details_last_scrape_status as title,
                        'Latest character details scrape status' as description
                    from resolved r
                    join characters c on c.id = r.character_id
                    left join active_name an on an.character_id = r.character_id
                    where c.details_last_scraped_at is not null

                    union all

                    select
                        r.character_id,
                        an.name as character_name,
                        'NAME_INACTIVATED' as event_type,
                        cn.inactive_date as occurred_at,
                        cast(null as text) as world,
                        cast(null as text) as guild_name,
                        cast(null as text) as category,
                        cast(null as integer) as rank,
                        cast(null as bigint) as value,
                        cn.name as title,
                        'Character name became inactive' as description
                    from resolved r
                    join character_names cn on cn.character_id = r.character_id
                    left join active_name an on an.character_id = r.character_id
                    where cn.active is false
                      and cn.inactive_date is not null

                    union all

                    select
                        r.character_id,
                        an.name as character_name,
                        'DEATH' as event_type,
                        d.death_date as occurred_at,
                        cast(null as text) as world,
                        cast(null as text) as guild_name,
                        cast(null as text) as category,
                        cast(null as integer) as rank,
                        cast(null as bigint) as value,
                        d.killed_by as title,
                        'Character death recorded from Tibia character details' as description
                    from resolved r
                    join character_deaths d on d.character_id = r.character_id
                    left join active_name an on an.character_id = r.character_id
                    where d.death_date is not null

                    union all

                    select
                        r.character_id,
                        an.name as character_name,
                        'WORLD_LEFT' as event_type,
                        cw.inactive_date as occurred_at,
                        w.name as world,
                        cast(null as text) as guild_name,
                        cast(null as text) as category,
                        cast(null as integer) as rank,
                        cast(null as bigint) as value,
                        w.name as title,
                        'Character world history transition observed' as description
                    from resolved r
                    join character_worlds cw on cw.character_id = r.character_id
                    join worlds w on w.id = cw.world_id
                    left join active_name an on an.character_id = r.character_id
                    where cw.active is false
                      and cw.inactive_date is not null

                    union all

                    select
                        r.character_id,
                        an.name as character_name,
                        case e.event_type
                            when 'JOINED' then 'GUILD_JOINED'
                            when 'LEFT' then 'GUILD_LEFT'
                            when 'TRANSFERRED' then 'GUILD_TRANSFERRED'
                            else 'GUILD_EVENT'
                        end as event_type,
                        e.observed_at as occurred_at,
                        coalesce(to_world.name, from_world.name) as world,
                        coalesce(to_guild.name, from_guild.name) as guild_name,
                        cast(null as text) as category,
                        cast(null as integer) as rank,
                        cast(null as bigint) as value,
                        e.event_type as title,
                        e.description
                    from resolved r
                    join guild_membership_events e on e.character_id = r.character_id
                    left join guilds from_guild on from_guild.id = e.from_guild_id
                    left join guilds to_guild on to_guild.id = e.to_guild_id
                    left join worlds from_world on from_world.id = from_guild.world_id
                    left join worlds to_world on to_world.id = to_guild.world_id
                    left join active_name an on an.character_id = r.character_id
                    where e.observed_at is not null
                ) events
                where (cast(:fromInstant as timestamp with time zone) is null or events.occurred_at >= :fromInstant)
                  and (cast(:toInstant as timestamp with time zone) is null or events.occurred_at <= :toInstant)
                order by events.occurred_at desc, events.event_type asc
                limit :limit
                """, prepareParams(params), this::mapTimelineEvent);
    }
}
