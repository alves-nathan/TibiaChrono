#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${1:-.}"
cd "$PROJECT_DIR"

PATCH_FILE="$(mktemp)"
cleanup() { rm -f "$PATCH_FILE"; }
trap cleanup EXIT

cat > "$PATCH_FILE" <<'PATCH'
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/application/scheduler/GuildScrapeScheduler.java tibia_guild_work/src/main/java/com/nathan/tibiastats/application/scheduler/GuildScrapeScheduler.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/application/scheduler/GuildScrapeScheduler.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/application/scheduler/GuildScrapeScheduler.java	2026-05-27 19:55:21.867172889 +0000
@@ -0,0 +1,53 @@
+package com.nathan.tibiastats.application.scheduler;
+
+import com.nathan.tibiastats.application.service.GuildScrapeService;
+import com.nathan.tibiastats.application.service.ScrapeJobResult;
+import com.nathan.tibiastats.application.service.ScrapeJobService;
+import com.nathan.tibiastats.config.GuildScrapeProperties;
+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
+import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
+import org.springframework.scheduling.annotation.Scheduled;
+import org.springframework.stereotype.Component;
+
+@Component
+@ConditionalOnProperty(prefix = "tibiastats.scrape.guilds", name = "enabled", havingValue = "true", matchIfMissing = false)
+public class GuildScrapeScheduler {
+    private static final Logger log = LoggerFactory.getLogger(GuildScrapeScheduler.class);
+    private static final String LOG_PREFIX = "[GUILD_SCRAPER]";
+
+    private final GuildScrapeService guildScrapeService;
+    private final GuildScrapeProperties properties;
+    private final ScrapeJobService scrapeJobService;
+
+    public GuildScrapeScheduler(GuildScrapeService guildScrapeService,
+                                GuildScrapeProperties properties,
+                                ScrapeJobService scrapeJobService) {
+        this.guildScrapeService = guildScrapeService;
+        this.properties = properties;
+        this.scrapeJobService = scrapeJobService;
+    }
+
+    @Scheduled(
+            fixedDelayString = "${tibiastats.scrape.guilds.rate-ms:3600000}",
+            initialDelayString = "${tibiastats.scrape.guilds.initial-delay-ms:30000}"
+    )
+    public void run() {
+        if (!properties.isEnabled()) {
+            log.debug("{} Scheduler disabled", LOG_PREFIX);
+            return;
+        }
+
+        Long jobId = scrapeJobService.start(ScrapeJobService.GUILD_SCRAPER);
+        log.info("{} Scheduler tick started. listEnabled={}, detailsEnabled={}, worldLimit={}, guildLimit={}",
+                LOG_PREFIX, properties.isListEnabled(), properties.isDetailsEnabled(), properties.getWorldLimit(), properties.getGuildLimit());
+        try {
+            ScrapeJobResult result = guildScrapeService.updateKnownGuilds();
+            scrapeJobService.finishSuccess(jobId, result);
+            log.info("{} Scheduler tick finished: {}", LOG_PREFIX, result);
+        } catch (Exception e) {
+            scrapeJobService.finishFailure(jobId, ScrapeJobResult.empty(), e);
+            throw e;
+        }
+    }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java tibia_guild_work/src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java	2026-05-27 19:55:21.865547913 +0000
@@ -0,0 +1,156 @@
+package com.nathan.tibiastats.application.service;
+
+import com.nathan.tibiastats.domain.model.*;
+import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
+import com.nathan.tibiastats.infrastructure.persistence.SpringGuildRepository;
+import org.springframework.stereotype.Service;
+import org.springframework.transaction.annotation.Transactional;
+
+import java.time.Instant;
+import java.util.List;
+
+@Service
+public class GuildQueryService {
+    private final SpringGuildRepository guilds;
+    private final CharacterRepositoryPort characters;
+
+    public GuildQueryService(SpringGuildRepository guilds, CharacterRepositoryPort characters) {
+        this.guilds = guilds;
+        this.characters = characters;
+    }
+
+    @Transactional(readOnly = true)
+    public List<GuildView> findGuilds(String worldName, Boolean active) {
+        return guilds.findGuilds(worldName, active).stream().map(GuildView::from).toList();
+    }
+
+    @Transactional(readOnly = true)
+    public GuildView findGuild(String name) {
+        return guilds.findGuild(name).map(GuildView::from)
+                .orElseThrow(() -> new IllegalArgumentException("Guild not found: " + name));
+    }
+
+    @Transactional(readOnly = true)
+    public List<GuildMemberView> findMembers(String guildName, Boolean active) {
+        Guild guild = guilds.findGuild(guildName)
+                .orElseThrow(() -> new IllegalArgumentException("Guild not found: " + guildName));
+        return guilds.findMemberships(guild, active).stream().map(GuildMemberView::from).toList();
+    }
+
+    @Transactional(readOnly = true)
+    public List<GuildMembershipEventView> findEvents(String guildName,
+                                                     String characterName,
+                                                     GuildMembershipEventType type,
+                                                     Instant from,
+                                                     Instant to,
+                                                     int limit) {
+        Long guildId = guildName == null || guildName.isBlank()
+                ? null
+                : guilds.findGuild(guildName).map(Guild::getId)
+                    .orElseThrow(() -> new IllegalArgumentException("Guild not found: " + guildName));
+
+        Long characterId = null;
+        if (characterName != null && !characterName.isBlank()) {
+            characterId = characters.findByAnyName(characterName, Instant.EPOCH)
+                    .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterName))
+                    .getId();
+        }
+
+        return guilds.findEvents(guildId, characterId, type, from, to, limit).stream()
+                .map(GuildMembershipEventView::from)
+                .toList();
+    }
+
+    @Transactional(readOnly = true)
+    public List<GuildMemberView> findCharacterGuildHistory(String characterName) {
+        CharacterEntity character = characters.findByAnyName(characterName, Instant.EPOCH)
+                .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterName));
+        return guilds.findMembershipHistory(character.getId()).stream().map(GuildMemberView::from).toList();
+    }
+
+    public record GuildView(Long id,
+                            String name,
+                            String world,
+                            String description,
+                            String homepage,
+                            String logoUrl,
+                            java.time.LocalDate foundedAt,
+                            boolean active,
+                            Instant lastSeenAt,
+                            Instant lastScrapedAt) {
+        static GuildView from(Guild guild) {
+            return new GuildView(
+                    guild.getId(),
+                    guild.getName(),
+                    guild.getWorld() == null ? null : guild.getWorld().getName(),
+                    guild.getDescription(),
+                    guild.getHomepage(),
+                    guild.getLogoUrl(),
+                    guild.getFoundedAt(),
+                    guild.isActive(),
+                    guild.getLastSeenAt(),
+                    guild.getLastScrapedAt()
+            );
+        }
+    }
+
+    public record GuildMemberView(Long membershipId,
+                                  Long guildId,
+                                  String guildName,
+                                  Long characterId,
+                                  String characterName,
+                                  String rankName,
+                                  String title,
+                                  String vocation,
+                                  Integer level,
+                                  Instant joinedAt,
+                                  Instant firstSeenAt,
+                                  Instant lastSeenAt,
+                                  Instant leftAt,
+                                  boolean active) {
+        static GuildMemberView from(GuildMembership membership) {
+            return new GuildMemberView(
+                    membership.getId(),
+                    membership.getGuild() == null ? null : membership.getGuild().getId(),
+                    membership.getGuild() == null ? null : membership.getGuild().getName(),
+                    membership.getCharacter() == null ? null : membership.getCharacter().getId(),
+                    membership.getCharacterNameSnapshot(),
+                    membership.getRankName(),
+                    membership.getTitle(),
+                    membership.getVocation(),
+                    membership.getLevel(),
+                    membership.getJoinedAt(),
+                    membership.getFirstSeenAt(),
+                    membership.getLastSeenAt(),
+                    membership.getLeftAt(),
+                    membership.isActive()
+            );
+        }
+    }
+
+    public record GuildMembershipEventView(Long id,
+                                           String eventType,
+                                           Long characterId,
+                                           String characterName,
+                                           Long fromGuildId,
+                                           String fromGuildName,
+                                           Long toGuildId,
+                                           String toGuildName,
+                                           Instant observedAt,
+                                           String description) {
+        static GuildMembershipEventView from(GuildMembershipEvent event) {
+            return new GuildMembershipEventView(
+                    event.getId(),
+                    event.getEventType().name(),
+                    event.getCharacter() == null ? null : event.getCharacter().getId(),
+                    event.getCharacterNameSnapshot(),
+                    event.getFromGuild() == null ? null : event.getFromGuild().getId(),
+                    event.getFromGuild() == null ? null : event.getFromGuild().getName(),
+                    event.getToGuild() == null ? null : event.getToGuild().getId(),
+                    event.getToGuild() == null ? null : event.getToGuild().getName(),
+                    event.getObservedAt(),
+                    event.getDescription()
+            );
+        }
+    }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/application/service/GuildScrapeService.java tibia_guild_work/src/main/java/com/nathan/tibiastats/application/service/GuildScrapeService.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/application/service/GuildScrapeService.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/application/service/GuildScrapeService.java	2026-05-27 19:54:51.662152593 +0000
@@ -0,0 +1,382 @@
+package com.nathan.tibiastats.application.service;
+
+import com.nathan.tibiastats.config.GuildScrapeProperties;
+import com.nathan.tibiastats.domain.model.*;
+import com.nathan.tibiastats.domain.port.GuildScrapePort;
+import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
+import com.nathan.tibiastats.infrastructure.persistence.SpringGuildRepository;
+import org.slf4j.Logger;
+import org.slf4j.LoggerFactory;
+import org.springframework.stereotype.Service;
+import org.springframework.transaction.annotation.Transactional;
+
+import java.time.Clock;
+import java.time.Instant;
+import java.util.*;
+import java.util.stream.Collectors;
+
+@Service
+public class GuildScrapeService {
+    private static final Logger log = LoggerFactory.getLogger(GuildScrapeService.class);
+    private static final String LOG_PREFIX = "[GUILD_SCRAPER]";
+
+    private final GuildScrapePort scraper;
+    private final SpringGuildRepository guilds;
+    private final WorldRepositoryPort worlds;
+    private final CharacterNamingService characterNamingService;
+    private final com.nathan.tibiastats.domain.port.CharacterRepositoryPort characters;
+    private final GuildScrapeProperties properties;
+    private final Clock clock;
+
+    public GuildScrapeService(GuildScrapePort scraper,
+                              SpringGuildRepository guilds,
+                              WorldRepositoryPort worlds,
+                              CharacterNamingService characterNamingService,
+                              com.nathan.tibiastats.domain.port.CharacterRepositoryPort characters,
+                              GuildScrapeProperties properties) {
+        this(scraper, guilds, worlds, characterNamingService, characters, properties, Clock.systemUTC());
+    }
+
+    GuildScrapeService(GuildScrapePort scraper,
+                       SpringGuildRepository guilds,
+                       WorldRepositoryPort worlds,
+                       CharacterNamingService characterNamingService,
+                       com.nathan.tibiastats.domain.port.CharacterRepositoryPort characters,
+                       GuildScrapeProperties properties,
+                       Clock clock) {
+        this.scraper = scraper;
+        this.guilds = guilds;
+        this.worlds = worlds;
+        this.characterNamingService = characterNamingService;
+        this.characters = characters;
+        this.properties = properties;
+        this.clock = clock;
+    }
+
+    public ScrapeJobResult updateKnownGuilds() {
+        int processed = 0;
+        int created = 0;
+        int updated = 0;
+        int failed = 0;
+
+        if (properties.isListEnabled()) {
+            List<World> allWorlds = worlds.findAll();
+            if (properties.getWorldLimit() > 0 && allWorlds.size() > properties.getWorldLimit()) {
+                allWorlds = allWorlds.subList(0, properties.getWorldLimit());
+            }
+
+            for (World world : allWorlds) {
+                try {
+                    GuildListResult result = updateGuildListForWorld(world.getName());
+                    processed += result.processed();
+                    created += result.created();
+                    updated += result.updated();
+                    sleepBetweenPages();
+                } catch (Exception e) {
+                    failed++;
+                    log.warn("{} Failed to update guild list for world={}: {}", LOG_PREFIX, world.getName(), e.getMessage());
+                }
+            }
+        }
+
+        if (properties.isDetailsEnabled()) {
+            for (Guild guild : guilds.findActiveForDetailsRefresh(properties.getGuildLimit())) {
+                try {
+                    GuildDetailResult result = updateGuildDetail(guild.getName());
+                    processed += result.membersSeen();
+                    created += result.membershipsOpened();
+                    updated += result.membershipsUpdated() + result.membershipsClosed();
+                    sleepBetweenPages();
+                } catch (Exception e) {
+                    failed++;
+                    log.warn("{} Failed to update guild detail for guild={}: {}", LOG_PREFIX, guild.getName(), e.getMessage());
+                }
+            }
+        }
+
+        return ScrapeJobResult.of(processed, created, updated, failed);
+    }
+
+    @Transactional
+    public GuildListResult updateGuildListForWorld(String worldName) {
+        Instant observedAt = clock.instant();
+        World world = ensureWorld(worldName);
+        int processed = 0;
+        int created = 0;
+        int updated = 0;
+
+        for (GuildScrapePort.GuildListItem item : scraper.fetchGuildList(worldName)) {
+            if (isBlank(item.name())) continue;
+            processed++;
+            UpsertResult result = upsertGuild(item.name(), item.worldName(), item.description(), null, null, null, item.active(), observedAt);
+            if (result.created()) created++; else updated++;
+            Guild guild = result.guild();
+            if (guild.getWorld() == null) guild.setWorld(world);
+            guilds.saveGuild(guild);
+        }
+
+        log.info("{} Guild list updated: world={}, processed={}, created={}, updated={}", LOG_PREFIX, worldName, processed, created, updated);
+        return new GuildListResult(processed, created, updated);
+    }
+
+    @Transactional
+    public GuildDetailResult updateGuildDetail(String guildName) {
+        Instant observedAt = clock.instant();
+        GuildScrapePort.GuildDetail detail = scraper.fetchGuildDetail(guildName);
+        if (isBlank(detail.name())) {
+            throw new IllegalStateException("Guild detail did not contain a guild name for '" + guildName + "'");
+        }
+
+        UpsertResult result = upsertGuild(
+                detail.name(),
+                detail.worldName(),
+                detail.description(),
+                detail.homepage(),
+                detail.logoUrl(),
+                detail.foundedAt(),
+                true,
+                observedAt
+        );
+        Guild guild = result.guild();
+        guild.setLastScrapedAt(observedAt);
+        guilds.saveGuild(guild);
+
+        saveSnapshot(guild, detail, observedAt);
+
+        Map<Long, GuildMembership> activeBefore = guilds.findActiveMemberships(guild).stream()
+                .filter(m -> m.getCharacter() != null && m.getCharacter().getId() != null)
+                .collect(Collectors.toMap(m -> m.getCharacter().getId(), m -> m, (a, b) -> a, LinkedHashMap::new));
+
+        Set<Long> seenCharacterIds = new LinkedHashSet<>();
+        int opened = 0;
+        int updated = 0;
+        int closed = 0;
+        int transfers = 0;
+
+        for (GuildScrapePort.Member member : nullSafe(detail.members())) {
+            if (isBlank(member.name())) continue;
+
+            CharacterEntity character = characterNamingService.resolveObservedName(member.name());
+            updateCharacterSnapshot(character, member);
+            Long characterId = character.getId();
+            seenCharacterIds.add(characterId);
+
+            Optional<GuildMembership> currentActive = guilds.findActiveMembershipForCharacter(characterId);
+            if (currentActive.isEmpty()) {
+                GuildMembership membership = openMembership(guild, character, member, observedAt);
+                guilds.saveMembership(membership);
+                saveEvent(character, member.name(), GuildMembershipEventType.JOINED, null, guild, observedAt,
+                        "Observed character joining guild " + guild.getName());
+                opened++;
+                continue;
+            }
+
+            GuildMembership active = currentActive.get();
+            if (active.getGuild().getId().equals(guild.getId())) {
+                refreshMembership(active, member, observedAt);
+                guilds.saveMembership(active);
+                updated++;
+            } else {
+                Guild previousGuild = active.getGuild();
+                closeMembership(active, observedAt);
+                guilds.saveMembership(active);
+
+                GuildMembership membership = openMembership(guild, character, member, observedAt);
+                guilds.saveMembership(membership);
+                saveEvent(character, member.name(), GuildMembershipEventType.TRANSFERRED, previousGuild, guild, observedAt,
+                        "Observed character transfer from " + previousGuild.getName() + " to " + guild.getName());
+                transfers++;
+                opened++;
+                closed++;
+            }
+        }
+
+        for (GuildMembership membership : activeBefore.values()) {
+            if (membership.getCharacter() == null || membership.getCharacter().getId() == null) continue;
+            if (seenCharacterIds.contains(membership.getCharacter().getId())) continue;
+
+            closeMembership(membership, observedAt);
+            guilds.saveMembership(membership);
+            saveEvent(membership.getCharacter(), membership.getCharacterNameSnapshot(), GuildMembershipEventType.LEFT, guild, null, observedAt,
+                    "Observed character leaving guild " + guild.getName());
+            closed++;
+        }
+
+        updateInvites(guild, detail, observedAt);
+
+        log.info("{} Guild detail updated: guild={}, membersSeen={}, opened={}, updated={}, closed={}, transfers={}",
+                LOG_PREFIX, guild.getName(), nullSafe(detail.members()).size(), opened, updated, closed, transfers);
+
+        return new GuildDetailResult(guild.getName(), nullSafe(detail.members()).size(), opened, updated, closed, transfers);
+    }
+
+    private UpsertResult upsertGuild(String name,
+                                     String worldName,
+                                     String description,
+                                     String homepage,
+                                     String logoUrl,
+                                     java.time.LocalDate foundedAt,
+                                     boolean active,
+                                     Instant observedAt) {
+        String normalizedName = SpringGuildRepository.normalizeGuildName(name);
+        Guild guild = guilds.findGuild(name).orElseGet(Guild::new);
+        boolean created = guild.getId() == null;
+
+        guild.setName(normalizeDisplayName(name));
+        guild.setNormalizedName(normalizedName);
+        guild.setWorld(ensureWorld(firstNonBlank(worldName, guild.getWorld() == null ? null : guild.getWorld().getName())));
+        if (!isBlank(description)) guild.setDescription(description.trim());
+        if (!isBlank(homepage)) guild.setHomepage(homepage.trim());
+        if (!isBlank(logoUrl)) guild.setLogoUrl(logoUrl.trim());
+        if (foundedAt != null) guild.setFoundedAt(foundedAt);
+        guild.setActive(active);
+        guild.setLastSeenAt(observedAt);
+
+        return new UpsertResult(guilds.saveGuild(guild), created);
+    }
+
+    private World ensureWorld(String worldName) {
+        String value = firstNonBlank(worldName, "Unknown");
+        return worlds.findByName(value).orElseGet(() -> worlds.save(new World(value, null, null)));
+    }
+
+    private void saveSnapshot(Guild guild, GuildScrapePort.GuildDetail detail, Instant observedAt) {
+        GuildSnapshot snapshot = new GuildSnapshot();
+        snapshot.setGuild(guild);
+        snapshot.setScrapedAt(observedAt);
+        snapshot.setMemberCount(detail.memberCount());
+        snapshot.setOnlineCount(detail.onlineCount());
+        snapshot.setRawHash(detail.rawHash());
+        guilds.saveSnapshot(snapshot);
+    }
+
+    private GuildMembership openMembership(Guild guild, CharacterEntity character, GuildScrapePort.Member member, Instant observedAt) {
+        GuildMembership membership = new GuildMembership();
+        membership.setGuild(guild);
+        membership.setCharacter(character);
+        membership.setCharacterNameSnapshot(normalizeDisplayName(member.name()));
+        membership.setRankName(blankToNull(member.rankName()));
+        membership.setTitle(blankToNull(member.title()));
+        membership.setVocation(blankToNull(member.vocation()));
+        membership.setLevel(member.level());
+        membership.setJoinedAt(observedAt);
+        membership.setFirstSeenAt(observedAt);
+        membership.setLastSeenAt(observedAt);
+        membership.setActive(true);
+        return membership;
+    }
+
+    private void refreshMembership(GuildMembership membership, GuildScrapePort.Member member, Instant observedAt) {
+        membership.setCharacterNameSnapshot(normalizeDisplayName(member.name()));
+        membership.setRankName(blankToNull(member.rankName()));
+        membership.setTitle(blankToNull(member.title()));
+        membership.setVocation(blankToNull(member.vocation()));
+        membership.setLevel(member.level());
+        membership.setLastSeenAt(observedAt);
+        membership.setActive(true);
+        membership.setLeftAt(null);
+    }
+
+    private void closeMembership(GuildMembership membership, Instant observedAt) {
+        membership.setActive(false);
+        membership.setLeftAt(observedAt);
+        membership.setLastSeenAt(observedAt);
+    }
+
+    private void saveEvent(CharacterEntity character,
+                           String nameSnapshot,
+                           GuildMembershipEventType type,
+                           Guild fromGuild,
+                           Guild toGuild,
+                           Instant observedAt,
+                           String description) {
+        GuildMembershipEvent event = new GuildMembershipEvent();
+        event.setCharacter(character);
+        event.setCharacterNameSnapshot(normalizeDisplayName(nameSnapshot));
+        event.setEventType(type);
+        event.setFromGuild(fromGuild);
+        event.setToGuild(toGuild);
+        event.setObservedAt(observedAt);
+        event.setDescription(description);
+        guilds.saveEvent(event);
+    }
+
+    private void updateInvites(Guild guild, GuildScrapePort.GuildDetail detail, Instant observedAt) {
+        Set<String> seen = new HashSet<>();
+        for (GuildScrapePort.Invite invite : nullSafe(detail.invites())) {
+            if (isBlank(invite.characterName())) continue;
+            String normalized = invite.characterName().trim().toLowerCase(Locale.ROOT);
+            seen.add(normalized);
+            GuildInvite entity = guilds.findActiveInvite(guild.getId(), invite.characterName()).orElseGet(GuildInvite::new);
+            if (entity.getId() == null) {
+                entity.setGuild(guild);
+                entity.setCharacterName(normalizeDisplayName(invite.characterName()));
+                entity.setInvitedAt(invite.invitedAt());
+                entity.setFirstSeenAt(observedAt);
+            }
+            entity.setLastSeenAt(observedAt);
+            entity.setActive(true);
+            guilds.saveInvite(entity);
+        }
+
+        for (GuildInvite active : guilds.findActiveInvites(guild.getId())) {
+            if (!seen.contains(active.getCharacterName().trim().toLowerCase(Locale.ROOT))) {
+                active.setActive(false);
+                active.setLastSeenAt(observedAt);
+                guilds.saveInvite(active);
+            }
+        }
+    }
+
+    private void updateCharacterSnapshot(CharacterEntity character, GuildScrapePort.Member member) {
+        if (member.level() != null) character.setLevel(member.level());
+        if (!isBlank(member.vocation())) {
+            characters.findVocationByNameOrPromotionName(member.vocation()).ifPresent(character::setVocation);
+        }
+        characters.save(character);
+    }
+
+    private void sleepBetweenPages() {
+        int delayMs = properties.getPageDelayMs();
+        if (delayMs <= 0) return;
+        try {
+            Thread.sleep(delayMs);
+        } catch (InterruptedException e) {
+            Thread.currentThread().interrupt();
+        }
+    }
+
+    private static <T> List<T> nullSafe(List<T> list) {
+        return list == null ? List.of() : list;
+    }
+
+    private static boolean isBlank(String value) {
+        return value == null || value.isBlank();
+    }
+
+    private static String blankToNull(String value) {
+        return isBlank(value) ? null : value.trim();
+    }
+
+    private static String firstNonBlank(String... values) {
+        for (String value : values) {
+            if (!isBlank(value)) return value.trim();
+        }
+        return "Unknown";
+    }
+
+    private static String normalizeDisplayName(String value) {
+        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
+    }
+
+    public record GuildListResult(int processed, int created, int updated) {}
+
+    public record GuildDetailResult(String guildName,
+                                    int membersSeen,
+                                    int membershipsOpened,
+                                    int membershipsUpdated,
+                                    int membershipsClosed,
+                                    int transfers) {}
+
+    private record UpsertResult(Guild guild, boolean created) {}
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/application/service/ScrapeJobService.java tibia_guild_work/src/main/java/com/nathan/tibiastats/application/service/ScrapeJobService.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/application/service/ScrapeJobService.java	2026-05-26 17:54:59.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/application/service/ScrapeJobService.java	2026-05-27 19:55:36.983935330 +0000
@@ -14,6 +14,7 @@
     public static final String WORLD_SCRAPER = "WORLD_SCRAPER";
     public static final String CHARACTER_DETAILS_SCRAPER = "CHARACTER_DETAILS_SCRAPER";
     public static final String HIGHSCORE_SCRAPER = "HIGHSCORE_SCRAPER";
+    public static final String GUILD_SCRAPER = "GUILD_SCRAPER";
 
     private final ScrapeJobExecutionRepository repository;
 
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/config/GuildScrapeProperties.java tibia_guild_work/src/main/java/com/nathan/tibiastats/config/GuildScrapeProperties.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/config/GuildScrapeProperties.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/config/GuildScrapeProperties.java	2026-05-27 19:53:43.715891890 +0000
@@ -0,0 +1,41 @@
+package com.nathan.tibiastats.config;
+
+import org.springframework.boot.context.properties.ConfigurationProperties;
+import org.springframework.context.annotation.Configuration;
+
+@Configuration
+@ConfigurationProperties(prefix = "tibiastats.scrape.guilds")
+public class GuildScrapeProperties {
+    private boolean enabled = true;
+    private long rateMs = 3_600_000L;
+    private long initialDelayMs = 30_000L;
+    private int worldLimit = 0;
+    private int guildLimit = 50;
+    private int pageDelayMs = 750;
+    private boolean listEnabled = true;
+    private boolean detailsEnabled = true;
+
+    public boolean isEnabled() { return enabled; }
+    public void setEnabled(boolean enabled) { this.enabled = enabled; }
+
+    public long getRateMs() { return Math.max(1_000L, rateMs); }
+    public void setRateMs(long rateMs) { this.rateMs = rateMs; }
+
+    public long getInitialDelayMs() { return Math.max(0L, initialDelayMs); }
+    public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
+
+    public int getWorldLimit() { return Math.max(0, worldLimit); }
+    public void setWorldLimit(int worldLimit) { this.worldLimit = worldLimit; }
+
+    public int getGuildLimit() { return Math.max(1, guildLimit); }
+    public void setGuildLimit(int guildLimit) { this.guildLimit = guildLimit; }
+
+    public int getPageDelayMs() { return Math.max(0, pageDelayMs); }
+    public void setPageDelayMs(int pageDelayMs) { this.pageDelayMs = pageDelayMs; }
+
+    public boolean isListEnabled() { return listEnabled; }
+    public void setListEnabled(boolean listEnabled) { this.listEnabled = listEnabled; }
+
+    public boolean isDetailsEnabled() { return detailsEnabled; }
+    public void setDetailsEnabled(boolean detailsEnabled) { this.detailsEnabled = detailsEnabled; }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/Guild.java tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/Guild.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/Guild.java	2026-05-26 14:21:17.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/Guild.java	2026-05-27 19:53:14.349539356 +0000
@@ -2,6 +2,9 @@
 
 import jakarta.persistence.*;
 
+import java.time.Instant;
+import java.time.LocalDate;
+
 @Entity
 @Table(name = "guilds")
 public class Guild {
@@ -12,31 +15,69 @@
     @Column(nullable = false, unique = true)
     private String name;
 
+    @Column(name = "normalized_name", nullable = false, unique = true)
+    private String normalizedName;
+
     @ManyToOne(optional = false)
     @JoinColumn(name = "world_id")
     private World world;
 
-    public Long getId() {
-        return id;
-    }
-
-    public void setId(Long id) {
-        this.id = id;
-    }
-
-    public String getName() {
-        return name;
-    }
-
-    public void setName(String name) {
-        this.name = name;
-    }
-
-    public World getWorld() {
-        return world;
-    }
-
-    public void setWorld(World world) {
-        this.world = world;
-    }
-}
\ No newline at end of file
+    @Column(columnDefinition = "text")
+    private String description;
+
+    private String homepage;
+
+    @Column(name = "logo_url")
+    private String logoUrl;
+
+    @Column(name = "founded_at")
+    private LocalDate foundedAt;
+
+    @Column(nullable = false)
+    private boolean active = true;
+
+    @Column(name = "disband_condition")
+    private String disbandCondition;
+
+    @Column(name = "last_seen_at")
+    private Instant lastSeenAt;
+
+    @Column(name = "last_scraped_at")
+    private Instant lastScrapedAt;
+
+    public Long getId() { return id; }
+    public void setId(Long id) { this.id = id; }
+
+    public String getName() { return name; }
+    public void setName(String name) { this.name = name; }
+
+    public String getNormalizedName() { return normalizedName; }
+    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
+
+    public World getWorld() { return world; }
+    public void setWorld(World world) { this.world = world; }
+
+    public String getDescription() { return description; }
+    public void setDescription(String description) { this.description = description; }
+
+    public String getHomepage() { return homepage; }
+    public void setHomepage(String homepage) { this.homepage = homepage; }
+
+    public String getLogoUrl() { return logoUrl; }
+    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
+
+    public LocalDate getFoundedAt() { return foundedAt; }
+    public void setFoundedAt(LocalDate foundedAt) { this.foundedAt = foundedAt; }
+
+    public boolean isActive() { return active; }
+    public void setActive(boolean active) { this.active = active; }
+
+    public String getDisbandCondition() { return disbandCondition; }
+    public void setDisbandCondition(String disbandCondition) { this.disbandCondition = disbandCondition; }
+
+    public Instant getLastSeenAt() { return lastSeenAt; }
+    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
+
+    public Instant getLastScrapedAt() { return lastScrapedAt; }
+    public void setLastScrapedAt(Instant lastScrapedAt) { this.lastScrapedAt = lastScrapedAt; }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/GuildInvite.java tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/GuildInvite.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/GuildInvite.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/GuildInvite.java	2026-05-27 19:53:14.353352089 +0000
@@ -0,0 +1,54 @@
+package com.nathan.tibiastats.domain.model;
+
+import jakarta.persistence.*;
+
+import java.time.Instant;
+import java.time.LocalDate;
+
+@Entity
+@Table(name = "guild_invites")
+public class GuildInvite {
+    @Id
+    @GeneratedValue(strategy = GenerationType.IDENTITY)
+    private Long id;
+
+    @ManyToOne(optional = false, fetch = FetchType.LAZY)
+    @JoinColumn(name = "guild_id")
+    private Guild guild;
+
+    @Column(name = "character_name", nullable = false)
+    private String characterName;
+
+    @Column(name = "invited_at")
+    private LocalDate invitedAt;
+
+    @Column(name = "first_seen_at", nullable = false)
+    private Instant firstSeenAt;
+
+    @Column(name = "last_seen_at", nullable = false)
+    private Instant lastSeenAt;
+
+    @Column(nullable = false)
+    private boolean active = true;
+
+    public Long getId() { return id; }
+    public void setId(Long id) { this.id = id; }
+
+    public Guild getGuild() { return guild; }
+    public void setGuild(Guild guild) { this.guild = guild; }
+
+    public String getCharacterName() { return characterName; }
+    public void setCharacterName(String characterName) { this.characterName = characterName; }
+
+    public LocalDate getInvitedAt() { return invitedAt; }
+    public void setInvitedAt(LocalDate invitedAt) { this.invitedAt = invitedAt; }
+
+    public Instant getFirstSeenAt() { return firstSeenAt; }
+    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
+
+    public Instant getLastSeenAt() { return lastSeenAt; }
+    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
+
+    public boolean isActive() { return active; }
+    public void setActive(boolean active) { this.active = active; }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/GuildMembership.java tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/GuildMembership.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/GuildMembership.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/GuildMembership.java	2026-05-27 19:53:14.351086677 +0000
@@ -0,0 +1,87 @@
+package com.nathan.tibiastats.domain.model;
+
+import jakarta.persistence.*;
+
+import java.time.Instant;
+
+@Entity
+@Table(name = "guild_memberships")
+public class GuildMembership {
+    @Id
+    @GeneratedValue(strategy = GenerationType.IDENTITY)
+    private Long id;
+
+    @ManyToOne(optional = false, fetch = FetchType.LAZY)
+    @JoinColumn(name = "guild_id")
+    private Guild guild;
+
+    @ManyToOne(optional = false, fetch = FetchType.LAZY)
+    @JoinColumn(name = "character_id")
+    private CharacterEntity character;
+
+    @Column(name = "character_name_snapshot", nullable = false)
+    private String characterNameSnapshot;
+
+    @Column(name = "rank_name")
+    private String rankName;
+
+    private String title;
+
+    private String vocation;
+
+    private Integer level;
+
+    @Column(name = "joined_at", nullable = false)
+    private Instant joinedAt;
+
+    @Column(name = "first_seen_at", nullable = false)
+    private Instant firstSeenAt;
+
+    @Column(name = "last_seen_at", nullable = false)
+    private Instant lastSeenAt;
+
+    @Column(name = "left_at")
+    private Instant leftAt;
+
+    @Column(nullable = false)
+    private boolean active = true;
+
+    public Long getId() { return id; }
+    public void setId(Long id) { this.id = id; }
+
+    public Guild getGuild() { return guild; }
+    public void setGuild(Guild guild) { this.guild = guild; }
+
+    public CharacterEntity getCharacter() { return character; }
+    public void setCharacter(CharacterEntity character) { this.character = character; }
+
+    public String getCharacterNameSnapshot() { return characterNameSnapshot; }
+    public void setCharacterNameSnapshot(String characterNameSnapshot) { this.characterNameSnapshot = characterNameSnapshot; }
+
+    public String getRankName() { return rankName; }
+    public void setRankName(String rankName) { this.rankName = rankName; }
+
+    public String getTitle() { return title; }
+    public void setTitle(String title) { this.title = title; }
+
+    public String getVocation() { return vocation; }
+    public void setVocation(String vocation) { this.vocation = vocation; }
+
+    public Integer getLevel() { return level; }
+    public void setLevel(Integer level) { this.level = level; }
+
+    public Instant getJoinedAt() { return joinedAt; }
+    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
+
+    public Instant getFirstSeenAt() { return firstSeenAt; }
+    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
+
+    public Instant getLastSeenAt() { return lastSeenAt; }
+    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
+
+    public Instant getLeftAt() { return leftAt; }
+    public void setLeftAt(Instant leftAt) { this.leftAt = leftAt; }
+
+    public boolean isActive() { return active; }
+    public void setActive(boolean active) { this.active = active; }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/GuildMembershipEvent.java tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/GuildMembershipEvent.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/GuildMembershipEvent.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/GuildMembershipEvent.java	2026-05-27 19:53:14.352393945 +0000
@@ -0,0 +1,62 @@
+package com.nathan.tibiastats.domain.model;
+
+import jakarta.persistence.*;
+
+import java.time.Instant;
+
+@Entity
+@Table(name = "guild_membership_events")
+public class GuildMembershipEvent {
+    @Id
+    @GeneratedValue(strategy = GenerationType.IDENTITY)
+    private Long id;
+
+    @ManyToOne(optional = false, fetch = FetchType.LAZY)
+    @JoinColumn(name = "character_id")
+    private CharacterEntity character;
+
+    @Column(name = "character_name_snapshot", nullable = false)
+    private String characterNameSnapshot;
+
+    @Enumerated(EnumType.STRING)
+    @Column(name = "event_type", nullable = false)
+    private GuildMembershipEventType eventType;
+
+    @ManyToOne(fetch = FetchType.LAZY)
+    @JoinColumn(name = "from_guild_id")
+    private Guild fromGuild;
+
+    @ManyToOne(fetch = FetchType.LAZY)
+    @JoinColumn(name = "to_guild_id")
+    private Guild toGuild;
+
+    @Column(name = "observed_at", nullable = false)
+    private Instant observedAt;
+
+    @Column(columnDefinition = "text")
+    private String description;
+
+    public Long getId() { return id; }
+    public void setId(Long id) { this.id = id; }
+
+    public CharacterEntity getCharacter() { return character; }
+    public void setCharacter(CharacterEntity character) { this.character = character; }
+
+    public String getCharacterNameSnapshot() { return characterNameSnapshot; }
+    public void setCharacterNameSnapshot(String characterNameSnapshot) { this.characterNameSnapshot = characterNameSnapshot; }
+
+    public GuildMembershipEventType getEventType() { return eventType; }
+    public void setEventType(GuildMembershipEventType eventType) { this.eventType = eventType; }
+
+    public Guild getFromGuild() { return fromGuild; }
+    public void setFromGuild(Guild fromGuild) { this.fromGuild = fromGuild; }
+
+    public Guild getToGuild() { return toGuild; }
+    public void setToGuild(Guild toGuild) { this.toGuild = toGuild; }
+
+    public Instant getObservedAt() { return observedAt; }
+    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
+
+    public String getDescription() { return description; }
+    public void setDescription(String description) { this.description = description; }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/GuildMembershipEventType.java tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/GuildMembershipEventType.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/GuildMembershipEventType.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/GuildMembershipEventType.java	2026-05-27 19:53:14.351651848 +0000
@@ -0,0 +1,7 @@
+package com.nathan.tibiastats.domain.model;
+
+public enum GuildMembershipEventType {
+    JOINED,
+    LEFT,
+    TRANSFERRED
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/GuildSnapshot.java tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/GuildSnapshot.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/model/GuildSnapshot.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/model/GuildSnapshot.java	2026-05-27 19:53:14.350269815 +0000
@@ -0,0 +1,47 @@
+package com.nathan.tibiastats.domain.model;
+
+import jakarta.persistence.*;
+
+import java.time.Instant;
+
+@Entity
+@Table(name = "guild_snapshots")
+public class GuildSnapshot {
+    @Id
+    @GeneratedValue(strategy = GenerationType.IDENTITY)
+    private Long id;
+
+    @ManyToOne(optional = false, fetch = FetchType.LAZY)
+    @JoinColumn(name = "guild_id")
+    private Guild guild;
+
+    @Column(name = "scraped_at", nullable = false)
+    private Instant scrapedAt;
+
+    @Column(name = "member_count")
+    private Integer memberCount;
+
+    @Column(name = "online_count")
+    private Integer onlineCount;
+
+    @Column(name = "raw_hash")
+    private String rawHash;
+
+    public Long getId() { return id; }
+    public void setId(Long id) { this.id = id; }
+
+    public Guild getGuild() { return guild; }
+    public void setGuild(Guild guild) { this.guild = guild; }
+
+    public Instant getScrapedAt() { return scrapedAt; }
+    public void setScrapedAt(Instant scrapedAt) { this.scrapedAt = scrapedAt; }
+
+    public Integer getMemberCount() { return memberCount; }
+    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
+
+    public Integer getOnlineCount() { return onlineCount; }
+    public void setOnlineCount(Integer onlineCount) { this.onlineCount = onlineCount; }
+
+    public String getRawHash() { return rawHash; }
+    public void setRawHash(String rawHash) { this.rawHash = rawHash; }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/port/GuildScrapePort.java tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/port/GuildScrapePort.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/domain/port/GuildScrapePort.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/domain/port/GuildScrapePort.java	2026-05-27 19:53:43.715232704 +0000
@@ -0,0 +1,45 @@
+package com.nathan.tibiastats.domain.port;
+
+import java.time.LocalDate;
+import java.util.List;
+
+public interface GuildScrapePort {
+    List<GuildListItem> fetchGuildList(String worldName);
+
+    GuildDetail fetchGuildDetail(String guildName);
+
+    record GuildListItem(
+            String name,
+            String worldName,
+            boolean active,
+            String description
+    ) {}
+
+    record GuildDetail(
+            String name,
+            String worldName,
+            String description,
+            String homepage,
+            String logoUrl,
+            LocalDate foundedAt,
+            Integer memberCount,
+            Integer onlineCount,
+            String rawHash,
+            List<Member> members,
+            List<Invite> invites
+    ) {}
+
+    record Member(
+            String name,
+            String rankName,
+            String title,
+            String vocation,
+            Integer level,
+            boolean online
+    ) {}
+
+    record Invite(
+            String characterName,
+            LocalDate invitedAt
+    ) {}
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java tibia_guild_work/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java	2026-05-27 19:54:16.198567797 +0000
@@ -0,0 +1,276 @@
+package com.nathan.tibiastats.infrastructure.adapter.scraper;
+
+import com.nathan.tibiastats.domain.port.GuildScrapePort;
+import org.jsoup.Jsoup;
+import org.jsoup.nodes.Document;
+import org.jsoup.nodes.Element;
+import org.jsoup.select.Elements;
+import org.springframework.stereotype.Component;
+
+import java.io.IOException;
+import java.net.URLEncoder;
+import java.nio.charset.StandardCharsets;
+import java.security.MessageDigest;
+import java.time.LocalDate;
+import java.time.format.DateTimeFormatter;
+import java.util.*;
+import java.util.regex.Matcher;
+import java.util.regex.Pattern;
+
+@Component
+public class JsoupGuildAdapter implements GuildScrapePort {
+    private static final String BASE_URL = "https://www.tibia.com/community/?subtopic=guilds";
+    private static final int TIMEOUT_MS = 30_000;
+    private static final Pattern CHARACTER_NAME_FROM_URL = Pattern.compile("[?&]name=([^&]+)", Pattern.CASE_INSENSITIVE);
+    private static final Pattern GUILD_NAME_FROM_URL = Pattern.compile("[?&]GuildName=([^&]+)", Pattern.CASE_INSENSITIVE);
+    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\b(\\d{1,5})\\b");
+    private static final List<String> VOCATION_WORDS = List.of(
+            "None", "No Vocation", "Druid", "Elder Druid", "Sorcerer", "Master Sorcerer",
+            "Knight", "Elite Knight", "Paladin", "Royal Paladin", "Monk", "Exalted Monk"
+    );
+
+    @Override
+    public List<GuildListItem> fetchGuildList(String worldName) {
+        try {
+            Document doc = Jsoup.connect(BASE_URL + "&world=" + encode(worldName))
+                    .userAgent("TibiaChrono/1.0 (+https://github.com/nathan)")
+                    .timeout(TIMEOUT_MS)
+                    .get();
+
+            Map<String, GuildListItem> items = new LinkedHashMap<>();
+            for (Element link : doc.select("a[href*=GuildName]")) {
+                String name = normalize(extractGuildName(link));
+                if (name.isBlank()) continue;
+                String rowText = link.parents().stream()
+                        .filter(e -> "tr".equalsIgnoreCase(e.tagName()))
+                        .findFirst()
+                        .map(Element::text)
+                        .orElse("");
+                boolean active = !rowText.toLowerCase(Locale.ROOT).contains("disband");
+                items.putIfAbsent(name.toLowerCase(Locale.ROOT), new GuildListItem(name, worldName, active, blankToNull(rowText)));
+            }
+            return List.copyOf(items.values());
+        } catch (IOException e) {
+            throw new IllegalStateException("Failed to fetch guild list for world '" + worldName + "'", e);
+        }
+    }
+
+    @Override
+    public GuildDetail fetchGuildDetail(String guildName) {
+        try {
+            Document doc = Jsoup.connect(BASE_URL + "&page=view&GuildName=" + encode(guildName))
+                    .userAgent("TibiaChrono/1.0 (+https://github.com/nathan)")
+                    .timeout(TIMEOUT_MS)
+                    .get();
+
+            String name = firstNonBlank(
+                    textOfFirst(doc, "h1"),
+                    normalize(guildName)
+            );
+            String pageText = doc.text();
+            String world = valueAfterLabel(pageText, "World:");
+            String homepage = valueAfterLabel(pageText, "Homepage:");
+            String description = valueAfterLabel(pageText, "Guild Description:");
+            LocalDate foundedAt = parseDate(valueAfterLabel(pageText, "Founded:"));
+            Integer memberCount = parseNumberBefore(pageText, "members");
+            Integer onlineCount = parseNumberBefore(pageText, "online");
+            String logoUrl = doc.select("img[src*=guildlogo], img[src*=guild]").stream()
+                    .map(img -> img.absUrl("src"))
+                    .filter(s -> s != null && !s.isBlank())
+                    .findFirst()
+                    .orElse(null);
+
+            List<Member> members = parseMembers(doc);
+            if (memberCount == null && !members.isEmpty()) {
+                memberCount = members.size();
+            }
+            if (onlineCount == null && !members.isEmpty()) {
+                onlineCount = (int) members.stream().filter(Member::online).count();
+            }
+
+            return new GuildDetail(
+                    name,
+                    blankToNull(world),
+                    blankToNull(description),
+                    blankToNull(homepage),
+                    blankToNull(logoUrl),
+                    foundedAt,
+                    memberCount,
+                    onlineCount,
+                    sha256(pageText),
+                    members,
+                    parseInvites(doc)
+            );
+        } catch (IOException e) {
+            throw new IllegalStateException("Failed to fetch guild detail for '" + guildName + "'", e);
+        }
+    }
+
+    private List<Member> parseMembers(Document doc) {
+        List<Member> members = new ArrayList<>();
+        String currentRank = null;
+
+        for (Element row : doc.select("tr")) {
+            String rowText = normalize(row.text());
+            if (rowText.isBlank()) continue;
+
+            if (row.select("a[href*=subtopic=characters], a[href*=characters]").isEmpty()) {
+                if (looksLikeRankHeader(rowText)) {
+                    currentRank = cleanupRank(rowText);
+                }
+                continue;
+            }
+
+            Element characterLink = row.select("a[href*=subtopic=characters], a[href*=characters]").first();
+            String name = normalize(extractCharacterName(characterLink));
+            if (name.isBlank()) continue;
+
+            List<String> cells = row.children().stream().map(Element::text).map(JsoupGuildAdapter::normalize).toList();
+            String vocation = findVocation(cells).orElse(null);
+            Integer level = findLevel(cells).orElse(null);
+            boolean online = rowText.toLowerCase(Locale.ROOT).contains("online");
+            String title = inferTitle(cells, name, currentRank, vocation, level);
+
+            members.add(new Member(name, blankToNull(currentRank), blankToNull(title), blankToNull(vocation), level, online));
+        }
+        return members;
+    }
+
+    private List<Invite> parseInvites(Document doc) {
+        List<Invite> invites = new ArrayList<>();
+        for (Element row : doc.select("tr")) {
+            String rowText = row.text().toLowerCase(Locale.ROOT);
+            if (!rowText.contains("invite") && !rowText.contains("invited")) continue;
+            Element characterLink = row.select("a[href*=subtopic=characters], a[href*=characters]").first();
+            if (characterLink == null) continue;
+            invites.add(new Invite(normalize(extractCharacterName(characterLink)), parseDate(row.text())));
+        }
+        return invites;
+    }
+
+    private static boolean looksLikeRankHeader(String text) {
+        String lower = text.toLowerCase(Locale.ROOT);
+        return lower.contains("leader") || lower.contains("vice") || lower.contains("member") || lower.contains("rank");
+    }
+
+    private static String cleanupRank(String text) {
+        return text.replace(":", "").replace("Rank", "").trim();
+    }
+
+    private static Optional<String> findVocation(List<String> cells) {
+        for (String cell : cells) {
+            for (String vocation : VOCATION_WORDS) {
+                if (cell.equalsIgnoreCase(vocation)) return Optional.of(vocation);
+            }
+        }
+        return Optional.empty();
+    }
+
+    private static Optional<Integer> findLevel(List<String> cells) {
+        for (String cell : cells) {
+            if (cell.matches("\\d{1,5}")) return Optional.of(Integer.parseInt(cell));
+        }
+        for (String cell : cells) {
+            Matcher matcher = LEVEL_PATTERN.matcher(cell);
+            if (matcher.find()) return Optional.of(Integer.parseInt(matcher.group(1)));
+        }
+        return Optional.empty();
+    }
+
+    private static String inferTitle(List<String> cells, String name, String rank, String vocation, Integer level) {
+        for (String cell : cells) {
+            if (cell.isBlank()) continue;
+            if (cell.equalsIgnoreCase(name)) continue;
+            if (rank != null && cell.equalsIgnoreCase(rank)) continue;
+            if (vocation != null && cell.equalsIgnoreCase(vocation)) continue;
+            if (level != null && cell.equals(String.valueOf(level))) continue;
+            String lower = cell.toLowerCase(Locale.ROOT);
+            if (lower.equals("online") || lower.equals("offline")) continue;
+            if (lower.contains("online") && cell.length() < 20) continue;
+            return cell;
+        }
+        return null;
+    }
+
+    private static String extractGuildName(Element link) {
+        String href = link.attr("href");
+        Matcher matcher = GUILD_NAME_FROM_URL.matcher(href);
+        if (matcher.find()) return decode(matcher.group(1));
+        return link.text();
+    }
+
+    private static String extractCharacterName(Element link) {
+        if (link == null) return "";
+        String href = link.attr("href");
+        Matcher matcher = CHARACTER_NAME_FROM_URL.matcher(href);
+        if (matcher.find()) return decode(matcher.group(1));
+        return link.text();
+    }
+
+    private static String valueAfterLabel(String text, String label) {
+        int index = text.indexOf(label);
+        if (index < 0) return null;
+        String tail = text.substring(index + label.length()).trim();
+        int end = tail.indexOf("  ");
+        if (end < 0) end = Math.min(tail.length(), 120);
+        return normalize(tail.substring(0, end));
+    }
+
+    private static Integer parseNumberBefore(String text, String word) {
+        Matcher matcher = Pattern.compile("(\\d+)\\s+" + Pattern.quote(word), Pattern.CASE_INSENSITIVE).matcher(text);
+        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
+    }
+
+    private static LocalDate parseDate(String text) {
+        if (text == null || text.isBlank()) return null;
+        List<DateTimeFormatter> formatters = List.of(
+                DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
+                DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH),
+                DateTimeFormatter.ISO_LOCAL_DATE
+        );
+        String cleaned = text.replace(",", "").trim();
+        for (DateTimeFormatter formatter : formatters) {
+            try { return LocalDate.parse(cleaned, formatter); } catch (Exception ignored) {}
+        }
+        return null;
+    }
+
+    private static String textOfFirst(Document doc, String selector) {
+        Element element = doc.selectFirst(selector);
+        return element == null ? null : normalize(element.text());
+    }
+
+    private static String firstNonBlank(String... values) {
+        for (String value : values) {
+            if (value != null && !value.isBlank()) return value;
+        }
+        return "";
+    }
+
+    private static String normalize(String value) {
+        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
+    }
+
+    private static String blankToNull(String value) {
+        return value == null || value.isBlank() ? null : value;
+    }
+
+    private static String encode(String value) {
+        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
+    }
+
+    private static String decode(String value) {
+        return java.net.URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
+    }
+
+    private static String sha256(String value) {
+        try {
+            byte[] digest = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
+            StringBuilder sb = new StringBuilder();
+            for (byte b : digest) sb.append(String.format("%02x", b));
+            return sb.toString();
+        } catch (Exception e) {
+            return null;
+        }
+    }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java tibia_guild_work/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java	2026-05-27 19:55:21.868487322 +0000
@@ -0,0 +1,87 @@
+package com.nathan.tibiastats.infrastructure.adapter.web.rest;
+
+import com.nathan.tibiastats.application.service.GuildQueryService;
+import com.nathan.tibiastats.application.service.GuildScrapeService;
+import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
+import org.springframework.format.annotation.DateTimeFormat;
+import org.springframework.http.HttpStatus;
+import org.springframework.web.bind.annotation.*;
+import org.springframework.web.server.ResponseStatusException;
+
+import java.time.Instant;
+import java.util.List;
+
+@RestController
+public class GuildController {
+    private final GuildQueryService guilds;
+    private final GuildScrapeService scraper;
+
+    public GuildController(GuildQueryService guilds, GuildScrapeService scraper) {
+        this.guilds = guilds;
+        this.scraper = scraper;
+    }
+
+    @GetMapping("/api/guilds")
+    public List<GuildQueryService.GuildView> listGuilds(
+            @RequestParam(required = false) String world,
+            @RequestParam(required = false) Boolean active
+    ) {
+        return guilds.findGuilds(world, active);
+    }
+
+    @GetMapping("/api/guilds/{name}")
+    public GuildQueryService.GuildView getGuild(@PathVariable String name) {
+        try {
+            return guilds.findGuild(name);
+        } catch (IllegalArgumentException e) {
+            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
+        }
+    }
+
+    @GetMapping("/api/guilds/{name}/members")
+    public List<GuildQueryService.GuildMemberView> getGuildMembers(
+            @PathVariable String name,
+            @RequestParam(required = false, defaultValue = "true") Boolean active
+    ) {
+        try {
+            return guilds.findMembers(name, active);
+        } catch (IllegalArgumentException e) {
+            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
+        }
+    }
+
+    @GetMapping("/api/guilds/{name}/events")
+    public List<GuildQueryService.GuildMembershipEventView> getGuildEvents(
+            @PathVariable String name,
+            @RequestParam(required = false) GuildMembershipEventType type,
+            @RequestParam(required = false) String characterName,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
+            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
+            @RequestParam(defaultValue = "100") int limit
+    ) {
+        try {
+            return guilds.findEvents(name, characterName, type, from, to, limit);
+        } catch (IllegalArgumentException e) {
+            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
+        }
+    }
+
+    @GetMapping("/api/characters/{name}/guild-history")
+    public List<GuildQueryService.GuildMemberView> getCharacterGuildHistory(@PathVariable String name) {
+        try {
+            return guilds.findCharacterGuildHistory(name);
+        } catch (IllegalArgumentException e) {
+            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
+        }
+    }
+
+    @PostMapping("/api/scrape/guilds/worlds/{world}")
+    public GuildScrapeService.GuildListResult scrapeGuildList(@PathVariable String world) {
+        return scraper.updateGuildListForWorld(world);
+    }
+
+    @PostMapping("/api/scrape/guilds/{guildName}")
+    public GuildScrapeService.GuildDetailResult scrapeGuildDetail(@PathVariable String guildName) {
+        return scraper.updateGuildDetail(guildName);
+    }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringGuildRepository.java tibia_guild_work/src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringGuildRepository.java
--- TibiaChrono-highscore-compact-rest-api/src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringGuildRepository.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringGuildRepository.java	2026-05-27 19:53:43.716351198 +0000
@@ -0,0 +1,206 @@
+package com.nathan.tibiastats.infrastructure.persistence;
+
+import com.nathan.tibiastats.domain.model.*;
+import org.springframework.data.domain.PageRequest;
+import org.springframework.data.jpa.repository.JpaRepository;
+import org.springframework.data.jpa.repository.Query;
+import org.springframework.data.repository.query.Param;
+import org.springframework.stereotype.Repository;
+
+import java.time.Instant;
+import java.util.List;
+import java.util.Optional;
+
+interface GuildJpa extends JpaRepository<Guild, Long> {
+    Optional<Guild> findByNameIgnoreCase(String name);
+
+    Optional<Guild> findByNormalizedName(String normalizedName);
+
+    @Query("""
+        select g
+          from Guild g
+          join fetch g.world w
+         where (:worldName is null or lower(w.name) = lower(:worldName))
+           and (:active is null or g.active = :active)
+         order by w.name asc, g.name asc
+        """)
+    List<Guild> findGuilds(@Param("worldName") String worldName, @Param("active") Boolean active);
+
+    @Query("""
+        select g
+          from Guild g
+          join fetch g.world w
+         where g.active = true
+         order by
+           case when g.lastScrapedAt is null then 0 else 1 end asc,
+           g.lastScrapedAt asc,
+           g.name asc
+        """)
+    List<Guild> findActiveForDetailsRefresh(org.springframework.data.domain.Pageable pageable);
+}
+
+interface GuildSnapshotJpa extends JpaRepository<GuildSnapshot, Long> {}
+
+interface GuildMembershipJpa extends JpaRepository<GuildMembership, Long> {
+    @Query("""
+        select gm
+          from GuildMembership gm
+          join fetch gm.character c
+         where gm.guild.id = :guildId
+           and gm.active = true
+         order by gm.rankName asc nulls last, gm.characterNameSnapshot asc
+        """)
+    List<GuildMembership> findActiveByGuildId(@Param("guildId") Long guildId);
+
+    @Query("""
+        select gm
+          from GuildMembership gm
+          join fetch gm.guild g
+          join fetch gm.character c
+         where c.id = :characterId
+           and gm.active = true
+        """)
+    Optional<GuildMembership> findActiveByCharacterId(@Param("characterId") Long characterId);
+
+    @Query("""
+        select gm
+          from GuildMembership gm
+          join fetch gm.guild g
+          join fetch gm.character c
+         where c.id = :characterId
+         order by gm.joinedAt desc, gm.id desc
+        """)
+    List<GuildMembership> findHistoryByCharacterId(@Param("characterId") Long characterId);
+
+    @Query("""
+        select gm
+          from GuildMembership gm
+          join fetch gm.guild g
+          join fetch gm.character c
+         where gm.guild.id = :guildId
+           and (:active is null or gm.active = :active)
+         order by gm.active desc, gm.rankName asc nulls last, gm.characterNameSnapshot asc
+        """)
+    List<GuildMembership> findByGuildId(@Param("guildId") Long guildId, @Param("active") Boolean active);
+}
+
+interface GuildMembershipEventJpa extends JpaRepository<GuildMembershipEvent, Long> {
+    @Query("""
+        select e
+          from GuildMembershipEvent e
+          left join fetch e.fromGuild fg
+          left join fetch e.toGuild tg
+          join fetch e.character c
+         where (:guildId is null or fg.id = :guildId or tg.id = :guildId)
+           and (:characterId is null or c.id = :characterId)
+           and (:type is null or e.eventType = :type)
+           and (:from is null or e.observedAt >= :from)
+           and (:to is null or e.observedAt <= :to)
+         order by e.observedAt desc, e.id desc
+        """)
+    List<GuildMembershipEvent> findEvents(@Param("guildId") Long guildId,
+                                           @Param("characterId") Long characterId,
+                                           @Param("type") GuildMembershipEventType type,
+                                           @Param("from") Instant from,
+                                           @Param("to") Instant to,
+                                           org.springframework.data.domain.Pageable pageable);
+}
+
+interface GuildInviteJpa extends JpaRepository<GuildInvite, Long> {
+    @Query("""
+        select i
+          from GuildInvite i
+         where i.guild.id = :guildId
+           and i.active = true
+        """)
+    List<GuildInvite> findActiveByGuildId(@Param("guildId") Long guildId);
+
+    @Query("""
+        select i
+          from GuildInvite i
+         where i.guild.id = :guildId
+           and lower(i.characterName) = lower(:characterName)
+           and i.active = true
+        """)
+    Optional<GuildInvite> findActive(@Param("guildId") Long guildId, @Param("characterName") String characterName);
+}
+
+@Repository
+public class SpringGuildRepository {
+    private final GuildJpa guilds;
+    private final GuildSnapshotJpa snapshots;
+    private final GuildMembershipJpa memberships;
+    private final GuildMembershipEventJpa events;
+    private final GuildInviteJpa invites;
+
+    public SpringGuildRepository(GuildJpa guilds,
+                                 GuildSnapshotJpa snapshots,
+                                 GuildMembershipJpa memberships,
+                                 GuildMembershipEventJpa events,
+                                 GuildInviteJpa invites) {
+        this.guilds = guilds;
+        this.snapshots = snapshots;
+        this.memberships = memberships;
+        this.events = events;
+        this.invites = invites;
+    }
+
+    public Optional<Guild> findGuild(String name) {
+        return guilds.findByNormalizedName(normalizeGuildName(name))
+                .or(() -> guilds.findByNameIgnoreCase(name));
+    }
+
+    public Guild saveGuild(Guild guild) { return guilds.save(guild); }
+
+    public List<Guild> findGuilds(String worldName, Boolean active) { return guilds.findGuilds(blankToNull(worldName), active); }
+
+    public List<Guild> findActiveForDetailsRefresh(int limit) {
+        return guilds.findActiveForDetailsRefresh(PageRequest.of(0, Math.max(1, limit)));
+    }
+
+    public GuildSnapshot saveSnapshot(GuildSnapshot snapshot) { return snapshots.save(snapshot); }
+
+    public GuildMembership saveMembership(GuildMembership membership) { return memberships.save(membership); }
+
+    public List<GuildMembership> findActiveMemberships(Guild guild) { return memberships.findActiveByGuildId(guild.getId()); }
+
+    public Optional<GuildMembership> findActiveMembershipForCharacter(Long characterId) {
+        return memberships.findActiveByCharacterId(characterId);
+    }
+
+    public List<GuildMembership> findMemberships(Guild guild, Boolean active) {
+        return memberships.findByGuildId(guild.getId(), active);
+    }
+
+    public List<GuildMembership> findMembershipHistory(Long characterId) {
+        return memberships.findHistoryByCharacterId(characterId);
+    }
+
+    public GuildMembershipEvent saveEvent(GuildMembershipEvent event) { return events.save(event); }
+
+    public List<GuildMembershipEvent> findEvents(Long guildId,
+                                                 Long characterId,
+                                                 GuildMembershipEventType type,
+                                                 Instant from,
+                                                 Instant to,
+                                                 int limit) {
+        return events.findEvents(guildId, characterId, type, from, to, PageRequest.of(0, Math.max(1, limit)));
+    }
+
+    public GuildInvite saveInvite(GuildInvite invite) { return invites.save(invite); }
+
+    public Optional<GuildInvite> findActiveInvite(Long guildId, String characterName) {
+        return invites.findActive(guildId, characterName);
+    }
+
+    public List<GuildInvite> findActiveInvites(Long guildId) { return invites.findActiveByGuildId(guildId); }
+
+    public static String normalizeGuildName(String name) {
+        if (name == null) return "";
+        return name.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
+    }
+
+    private static String blankToNull(String value) {
+        return value == null || value.isBlank() ? null : value;
+    }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/resources/application.yml tibia_guild_work/src/main/resources/application.yml
--- TibiaChrono-highscore-compact-rest-api/src/main/resources/application.yml	2026-05-26 16:38:49.000000000 +0000
+++ tibia_guild_work/src/main/resources/application.yml	2026-05-27 19:55:36.988586833 +0000
@@ -36,6 +36,16 @@
       rate-ms: 60000           # 1 min between small batches during dev
       initial-delay-ms: 15000  # wait 15s after startup before first batch
       batch-size: 50            # adjustable through TIBIASTATS_SCRAPE_CHARACTER_DETAILS_BATCH_SIZE
+
+    guilds:
+      enabled: false            # keep disabled until you choose the cadence deliberately
+      rate-ms: 3600000          # 1h
+      initial-delay-ms: 30000
+      world-limit: 0
+      guild-limit: 50
+      page-delay-ms: 750
+      list-enabled: true
+      details-enabled: true
     highscores:
       enabled: true
       cron: "0 0 7 * * *"
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/main/resources/db/migration/V46__guild_scraper_history.sql tibia_guild_work/src/main/resources/db/migration/V46__guild_scraper_history.sql
--- TibiaChrono-highscore-compact-rest-api/src/main/resources/db/migration/V46__guild_scraper_history.sql	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/main/resources/db/migration/V46__guild_scraper_history.sql	2026-05-27 19:53:14.348852556 +0000
@@ -0,0 +1,100 @@
+-- Guild scraper and membership-history model.
+--
+-- Guild membership changes cannot be known at the exact instant they happen on Tibia.
+-- These tables store the moment the application observed the transition during scraping.
+
+ALTER TABLE guilds ADD COLUMN IF NOT EXISTS normalized_name TEXT;
+ALTER TABLE guilds ADD COLUMN IF NOT EXISTS description TEXT;
+ALTER TABLE guilds ADD COLUMN IF NOT EXISTS homepage TEXT;
+ALTER TABLE guilds ADD COLUMN IF NOT EXISTS logo_url TEXT;
+ALTER TABLE guilds ADD COLUMN IF NOT EXISTS founded_at DATE;
+ALTER TABLE guilds ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
+ALTER TABLE guilds ADD COLUMN IF NOT EXISTS disband_condition TEXT;
+ALTER TABLE guilds ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP WITH TIME ZONE;
+ALTER TABLE guilds ADD COLUMN IF NOT EXISTS last_scraped_at TIMESTAMP WITH TIME ZONE;
+
+UPDATE guilds
+   SET normalized_name = lower(trim(name))
+ WHERE normalized_name IS NULL
+   AND name IS NOT NULL;
+
+UPDATE guilds
+   SET active = TRUE
+ WHERE active IS NULL;
+
+ALTER TABLE guilds ALTER COLUMN normalized_name SET NOT NULL;
+ALTER TABLE guilds ALTER COLUMN active SET NOT NULL;
+
+CREATE UNIQUE INDEX IF NOT EXISTS uq_guilds_normalized_name
+    ON guilds (normalized_name);
+
+CREATE INDEX IF NOT EXISTS idx_guilds_world_active
+    ON guilds (world_id, active, last_scraped_at);
+
+CREATE TABLE IF NOT EXISTS guild_snapshots (
+    id BIGSERIAL PRIMARY KEY,
+    guild_id BIGINT NOT NULL REFERENCES guilds (id) ON DELETE CASCADE,
+    scraped_at TIMESTAMP WITH TIME ZONE NOT NULL,
+    member_count INTEGER,
+    online_count INTEGER,
+    raw_hash TEXT
+);
+
+CREATE INDEX IF NOT EXISTS idx_guild_snapshots_guild_scraped_at
+    ON guild_snapshots (guild_id, scraped_at DESC);
+
+CREATE TABLE IF NOT EXISTS guild_memberships (
+    id BIGSERIAL PRIMARY KEY,
+    guild_id BIGINT NOT NULL REFERENCES guilds (id) ON DELETE CASCADE,
+    character_id BIGINT NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
+    character_name_snapshot TEXT NOT NULL,
+    rank_name TEXT,
+    title TEXT,
+    vocation TEXT,
+    level INTEGER,
+    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
+    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
+    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
+    left_at TIMESTAMP WITH TIME ZONE,
+    active BOOLEAN NOT NULL DEFAULT TRUE
+);
+
+CREATE INDEX IF NOT EXISTS idx_guild_memberships_guild_active
+    ON guild_memberships (guild_id, active, rank_name, character_name_snapshot);
+
+CREATE INDEX IF NOT EXISTS idx_guild_memberships_character_period
+    ON guild_memberships (character_id, joined_at DESC, left_at DESC NULLS FIRST);
+
+CREATE UNIQUE INDEX IF NOT EXISTS uq_guild_memberships_one_active_character
+    ON guild_memberships (character_id)
+    WHERE active IS TRUE;
+
+CREATE TABLE IF NOT EXISTS guild_membership_events (
+    id BIGSERIAL PRIMARY KEY,
+    character_id BIGINT NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
+    character_name_snapshot TEXT NOT NULL,
+    event_type TEXT NOT NULL CHECK (event_type IN ('JOINED', 'LEFT', 'TRANSFERRED')),
+    from_guild_id BIGINT REFERENCES guilds (id) ON DELETE SET NULL,
+    to_guild_id BIGINT REFERENCES guilds (id) ON DELETE SET NULL,
+    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
+    description TEXT
+);
+
+CREATE INDEX IF NOT EXISTS idx_guild_membership_events_character_observed
+    ON guild_membership_events (character_id, observed_at DESC);
+
+CREATE INDEX IF NOT EXISTS idx_guild_membership_events_guilds_observed
+    ON guild_membership_events (from_guild_id, to_guild_id, observed_at DESC);
+
+CREATE TABLE IF NOT EXISTS guild_invites (
+    id BIGSERIAL PRIMARY KEY,
+    guild_id BIGINT NOT NULL REFERENCES guilds (id) ON DELETE CASCADE,
+    character_name TEXT NOT NULL,
+    invited_at DATE,
+    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
+    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
+    active BOOLEAN NOT NULL DEFAULT TRUE
+);
+
+CREATE INDEX IF NOT EXISTS idx_guild_invites_guild_active
+    ON guild_invites (guild_id, active, character_name);
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/test/java/com/nathan/tibiastats/AbstractPostgresTest.java tibia_guild_work/src/test/java/com/nathan/tibiastats/AbstractPostgresTest.java
--- TibiaChrono-highscore-compact-rest-api/src/test/java/com/nathan/tibiastats/AbstractPostgresTest.java	2026-05-27 18:37:22.000000000 +0000
+++ tibia_guild_work/src/test/java/com/nathan/tibiastats/AbstractPostgresTest.java	2026-05-27 19:55:36.990826104 +0000
@@ -46,6 +46,10 @@
                     character_worlds,
                     scrape_players,
                     scrapes,
+                    guild_invites,
+                    guild_membership_events,
+                    guild_memberships,
+                    guild_snapshots,
                     guild_characters,
                     guilds,
                     refresh_tokens,
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/test/java/com/nathan/tibiastats/guild/GuildScrapeServiceIntegrationTest.java tibia_guild_work/src/test/java/com/nathan/tibiastats/guild/GuildScrapeServiceIntegrationTest.java
--- TibiaChrono-highscore-compact-rest-api/src/test/java/com/nathan/tibiastats/guild/GuildScrapeServiceIntegrationTest.java	1970-01-01 00:00:00.000000000 +0000
+++ tibia_guild_work/src/test/java/com/nathan/tibiastats/guild/GuildScrapeServiceIntegrationTest.java	2026-05-27 19:56:05.630760124 +0000
@@ -0,0 +1,161 @@
+package com.nathan.tibiastats.guild;
+
+import com.nathan.tibiastats.AbstractPostgresTest;
+import com.nathan.tibiastats.application.service.GuildScrapeService;
+import com.nathan.tibiastats.domain.port.GuildScrapePort;
+import org.junit.jupiter.api.BeforeEach;
+import org.junit.jupiter.api.Test;
+import org.springframework.beans.factory.annotation.Autowired;
+import org.springframework.boot.test.context.SpringBootTest;
+import org.springframework.boot.test.context.TestConfiguration;
+import org.springframework.context.annotation.Bean;
+import org.springframework.context.annotation.Primary;
+
+import java.time.LocalDate;
+import java.util.List;
+import java.util.Map;
+import java.util.concurrent.ConcurrentHashMap;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+@SpringBootTest(properties = {
+        "tibiastats.scrape.guilds.enabled=false",
+        "tibiastats.scrape.highscores.enabled=false"
+})
+class GuildScrapeServiceIntegrationTest extends AbstractPostgresTest {
+    @Autowired GuildScrapeService service;
+    @Autowired FakeGuildScrapePort fake;
+
+    @BeforeEach
+    void setupFake() {
+        fake.clear();
+    }
+
+    @Test
+    void opensMembershipAndJoinEventWhenPlayerIsFirstSeenInGuild() {
+        fake.putDetail(detail("Raw Raw", member("Nathan Test", "Leader", "Boss", "Elite Knight", 500)));
+
+        GuildScrapeService.GuildDetailResult result = service.updateGuildDetail("Raw Raw");
+
+        assertThat(result.membersSeen()).isEqualTo(1);
+        assertThat(result.membershipsOpened()).isEqualTo(1);
+        assertThat(result.membershipsClosed()).isEqualTo(0);
+
+        Integer activeMemberships = jdbc.queryForObject("select count(*) from guild_memberships where active is true", Integer.class);
+        Integer joinEvents = jdbc.queryForObject("select count(*) from guild_membership_events where event_type = 'JOINED'", Integer.class);
+        String guildName = jdbc.queryForObject("select g.name from guild_memberships gm join guilds g on g.id = gm.guild_id where gm.active is true", String.class);
+
+        assertThat(activeMemberships).isEqualTo(1);
+        assertThat(joinEvents).isEqualTo(1);
+        assertThat(guildName).isEqualTo("Raw Raw");
+    }
+
+    @Test
+    void closesMembershipAndAddsLeftEventWhenPlayerDisappearsFromGuildSnapshot() {
+        fake.putDetail(detail("Raw Raw", member("Former Raw", "Member", null, "Royal Paladin", 300)));
+        service.updateGuildDetail("Raw Raw");
+
+        fake.putDetail(detail("Raw Raw"));
+        GuildScrapeService.GuildDetailResult result = service.updateGuildDetail("Raw Raw");
+
+        assertThat(result.membershipsClosed()).isEqualTo(1);
+
+        Integer activeMemberships = jdbc.queryForObject("select count(*) from guild_memberships where active is true", Integer.class);
+        Integer inactiveMemberships = jdbc.queryForObject("select count(*) from guild_memberships where active is false and left_at is not null", Integer.class);
+        Integer leftEvents = jdbc.queryForObject("select count(*) from guild_membership_events where event_type = 'LEFT'", Integer.class);
+
+        assertThat(activeMemberships).isZero();
+        assertThat(inactiveMemberships).isEqualTo(1);
+        assertThat(leftEvents).isEqualTo(1);
+    }
+
+    @Test
+    void transfersPlayerWhenSameCharacterIsSeenInAnotherGuild() {
+        fake.putDetail(detail("Raw Raw", member("Transfer Test", "Member", null, "Elder Druid", 250)));
+        service.updateGuildDetail("Raw Raw");
+
+        fake.putDetail(detail("Other Guild", member("Transfer Test", "Vice Leader", "Recruiter", "Elder Druid", 251)));
+        GuildScrapeService.GuildDetailResult result = service.updateGuildDetail("Other Guild");
+
+        assertThat(result.transfers()).isEqualTo(1);
+        assertThat(result.membershipsOpened()).isEqualTo(1);
+        assertThat(result.membershipsClosed()).isEqualTo(1);
+
+        String activeGuild = jdbc.queryForObject("""
+                select g.name
+                  from guild_memberships gm
+                  join guilds g on g.id = gm.guild_id
+                 where gm.active is true
+                """, String.class);
+        Integer transferredEvents = jdbc.queryForObject("select count(*) from guild_membership_events where event_type = 'TRANSFERRED'", Integer.class);
+        Integer inactiveOldGuilds = jdbc.queryForObject("""
+                select count(*)
+                  from guild_memberships gm
+                  join guilds g on g.id = gm.guild_id
+                 where gm.active is false
+                   and gm.left_at is not null
+                   and g.name = 'Raw Raw'
+                """, Integer.class);
+
+        assertThat(activeGuild).isEqualTo("Other Guild");
+        assertThat(transferredEvents).isEqualTo(1);
+        assertThat(inactiveOldGuilds).isEqualTo(1);
+    }
+
+    private static GuildScrapePort.GuildDetail detail(String guildName, GuildScrapePort.Member... members) {
+        return new GuildScrapePort.GuildDetail(
+                guildName,
+                "Antica",
+                "Test guild",
+                null,
+                null,
+                LocalDate.of(2026, 5, 27),
+                members.length,
+                0,
+                guildName + "-hash-" + members.length,
+                List.of(members),
+                List.of()
+        );
+    }
+
+    private static GuildScrapePort.Member member(String name, String rank, String title, String vocation, Integer level) {
+        return new GuildScrapePort.Member(name, rank, title, vocation, level, false);
+    }
+
+    @TestConfiguration
+    static class FakeConfig {
+        @Bean
+        @Primary
+        FakeGuildScrapePort fakeGuildScrapePort() {
+            return new FakeGuildScrapePort();
+        }
+    }
+
+    static class FakeGuildScrapePort implements GuildScrapePort {
+        private final Map<String, GuildDetail> details = new ConcurrentHashMap<>();
+
+        void putDetail(GuildDetail detail) {
+            details.put(detail.name().toLowerCase(java.util.Locale.ROOT), detail);
+        }
+
+        void clear() {
+            details.clear();
+        }
+
+        @Override
+        public List<GuildListItem> fetchGuildList(String worldName) {
+            return details.values().stream()
+                    .map(d -> new GuildListItem(d.name(), d.worldName(), true, d.description()))
+                    .toList();
+        }
+
+        @Override
+        public GuildDetail fetchGuildDetail(String guildName) {
+            GuildDetail detail = details.get(guildName.toLowerCase(java.util.Locale.ROOT));
+            if (detail == null) {
+                throw new IllegalArgumentException("No fake guild detail configured for " + guildName);
+            }
+            return detail;
+        }
+    }
+}
diff -ruN '--exclude=target' '--exclude=.git' TibiaChrono-highscore-compact-rest-api/src/test/resources/application-test.yml tibia_guild_work/src/test/resources/application-test.yml
--- TibiaChrono-highscore-compact-rest-api/src/test/resources/application-test.yml	2026-05-27 18:37:22.000000000 +0000
+++ tibia_guild_work/src/test/resources/application-test.yml	2026-05-27 19:55:36.989709155 +0000
@@ -39,3 +39,9 @@
       batch-size: 0
     highscores:
       enabled: false
+    guilds:
+      enabled: false
+      rate-ms: 3600000
+      initial-delay-ms: 3600000
+      guild-limit: 1
+      page-delay-ms: 0
PATCH

patch -p1 < "$PATCH_FILE"

echo "Guild scraper/history patch applied. Run: ./run-tests.sh"
