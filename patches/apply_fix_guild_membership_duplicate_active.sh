#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

SERVICE="src/main/java/com/nathan/tibiastats/application/service/GuildScrapeService.java"
REPO="src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringGuildRepository.java"

for file in "$SERVICE" "$REPO"; do
  if [[ ! -f "$file" ]]; then
    echo "ERROR: expected file not found: $file" >&2
    exit 1
  fi
done

cp "$SERVICE" "$SERVICE.bak-duplicate-active-guild-membership"
cp "$REPO" "$REPO.bak-duplicate-active-guild-membership"

python3 - <<'PY'
from pathlib import Path

service = Path('src/main/java/com/nathan/tibiastats/application/service/GuildScrapeService.java')
s = service.read_text()

old = '''            Optional<GuildMembership> currentActive = guilds.findActiveMembershipForCharacter(characterId);
            if (currentActive.isEmpty()) {
                GuildMembership membership = openMembership(guild, character, member, observedAt);
                guilds.saveMembership(membership);
                saveEvent(character, member.name(), GuildMembershipEventType.JOINED, null, guild, observedAt,
                        "Observed character joining guild " + guild.getName());
                opened++;
                continue;
            }

            GuildMembership active = currentActive.get();
            if (active.getGuild().getId().equals(guild.getId())) {
                refreshMembership(active, member, observedAt);
                guilds.saveMembership(active);
                updated++;
            } else {
                Guild previousGuild = active.getGuild();
                closeMembership(active, observedAt);
                guilds.saveMembership(active);

                GuildMembership membership = openMembership(guild, character, member, observedAt);
                guilds.saveMembership(membership);
                saveEvent(character, member.name(), GuildMembershipEventType.TRANSFERRED, previousGuild, guild, observedAt,
                        "Observed character transfer from " + previousGuild.getName() + " to " + guild.getName());
                transfers++;
                opened++;
                closed++;
            }
'''

new = '''            GuildMembership activeFromThisGuild = activeBefore.get(characterId);
            if (activeFromThisGuild != null) {
                refreshMembership(activeFromThisGuild, member, observedAt);
                guilds.saveMembership(activeFromThisGuild);
                updated++;
                continue;
            }

            Optional<GuildMembership> currentActive = guilds.findActiveMembershipForCharacter(characterId);
            if (currentActive.isEmpty()) {
                GuildMembership membership = openMembership(guild, character, member, observedAt);
                guilds.saveMembership(membership);
                saveEvent(character, member.name(), GuildMembershipEventType.JOINED, null, guild, observedAt,
                        "Observed character joining guild " + guild.getName());
                opened++;
                continue;
            }

            GuildMembership active = currentActive.get();
            if (active.getGuild() != null && active.getGuild().getId().equals(guild.getId())) {
                refreshMembership(active, member, observedAt);
                guilds.saveMembership(active);
                updated++;
            } else {
                Guild previousGuild = active.getGuild();
                closeMembership(active, observedAt);
                guilds.saveAndFlushMembership(active);

                GuildMembership membership = openMembership(guild, character, member, observedAt);
                guilds.saveMembership(membership);
                saveEvent(character, member.name(), GuildMembershipEventType.TRANSFERRED, previousGuild, guild, observedAt,
                        "Observed character transfer from " + safeGuildName(previousGuild) + " to " + guild.getName());
                transfers++;
                opened++;
                closed++;
            }
'''

if 'GuildMembership activeFromThisGuild = activeBefore.get(characterId);' not in s:
    if old not in s:
        raise SystemExit('ERROR: could not find GuildScrapeService membership decision block')
    s = s.replace(old, new)
else:
    s = s.replace('active.getGuild().getId().equals(guild.getId())', 'active.getGuild() != null && active.getGuild().getId().equals(guild.getId())')
    s = s.replace('guilds.saveMembership(active);\n\n                GuildMembership membership = openMembership(guild, character, member, observedAt);', 'guilds.saveAndFlushMembership(active);\n\n                GuildMembership membership = openMembership(guild, character, member, observedAt);')
    s = s.replace('"Observed character transfer from " + previousGuild.getName() + " to " + guild.getName()', '"Observed character transfer from " + safeGuildName(previousGuild) + " to " + guild.getName()')

if 'private static String safeGuildName(Guild guild)' not in s:
    marker = '    private static Instant toMembershipJoinedAt(GuildScrapePort.Member member, Instant observedAt) {'
    if marker not in s:
        raise SystemExit('ERROR: could not find insertion point for safeGuildName')
    s = s.replace(marker, '''    private static String safeGuildName(Guild guild) {
        return guild == null || isBlank(guild.getName()) ? "Unknown" : guild.getName();
    }

''' + marker)

service.write_text(s)

repo = Path('src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringGuildRepository.java')
s = repo.read_text()
old_query = '''    @Query("""
        select gm
          from GuildMembership gm
          join fetch gm.guild g
          join fetch gm.character c
         where c.id = :characterId
           and gm.active = true
        """)
    Optional<GuildMembership> findActiveByCharacterId(@Param("characterId") Long characterId);
'''
new_query = '''    @Query(value = """
        select gm.*
          from guild_memberships gm
         where gm.character_id = :characterId
           and gm.active is true
         limit 1
        """, nativeQuery = true)
    Optional<GuildMembership> findActiveByCharacterId(@Param("characterId") Long characterId);
'''
if 'select gm.*' not in s and old_query in s:
    s = s.replace(old_query, new_query)
elif 'select gm.*' not in s:
    raise SystemExit('ERROR: could not find findActiveByCharacterId query block')

if 'public GuildMembership saveAndFlushMembership(GuildMembership membership)' not in s:
    marker = '    public GuildMembership saveMembership(GuildMembership membership) { return memberships.save(membership); }\n'
    if marker not in s:
        raise SystemExit('ERROR: could not find saveMembership method')
    s = s.replace(marker, marker + '\n    public GuildMembership saveAndFlushMembership(GuildMembership membership) { return memberships.saveAndFlush(membership); }\n\n    public void flushMemberships() { memberships.flush(); }\n')

repo.write_text(s)
PY

echo "Guild duplicate active membership fix applied."
echo "Next: ./run-tests.sh"
