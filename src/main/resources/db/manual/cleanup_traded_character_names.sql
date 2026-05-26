-- Find rows currently polluted by the Tibia.com UI tag "(traded)".
select id, character_id, name, active, inactive_date
from character_names
where name ~* '\\s*\\(traded\\)\\s*$'
order by character_id, active desc, name;

-- Safe cleanup: rename only rows whose normalized name does not collide with another row.
update character_names cn
set name = trim(regexp_replace(cn.name, '\\s*\\(traded\\)\\s*$', '', 'i'))
where cn.name ~* '\\s*\\(traded\\)\\s*$'
  and not exists (
      select 1
      from character_names other_cn
      where other_cn.id <> cn.id
        and lower(other_cn.name) = lower(trim(regexp_replace(cn.name, '\\s*\\(traded\\)\\s*$', '', 'i')))
  );

-- Rows returned here need manual/merge review because the normalized name already exists elsewhere.
select
    cn.id,
    cn.character_id,
    cn.name as polluted_name,
    trim(regexp_replace(cn.name, '\\s*\\(traded\\)\\s*$', '', 'i')) as normalized_name,
    existing.id as existing_name_id,
    existing.character_id as existing_character_id,
    existing.active as existing_active
from character_names cn
join character_names existing
  on existing.id <> cn.id
 and lower(existing.name) = lower(trim(regexp_replace(cn.name, '\\s*\\(traded\\)\\s*$', '', 'i')))
where cn.name ~* '\\s*\\(traded\\)\\s*$'
order by cn.character_id, cn.name;
