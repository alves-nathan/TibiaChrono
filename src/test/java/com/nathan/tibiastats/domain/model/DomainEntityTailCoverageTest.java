package com.nathan.tibiastats.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEntityTailCoverageTest {
    @Test
    void guildInviteAndSnapshotExposePersistenceState() {
        Guild guild = new Guild();
        GuildInvite invite = new GuildInvite();
        Instant firstSeen = Instant.parse("2026-06-02T10:00:00Z");
        Instant lastSeen = Instant.parse("2026-06-02T11:00:00Z");
        invite.setId(1L);
        invite.setGuild(guild);
        invite.setCharacterName("Invited Paladin");
        invite.setInvitedAt(LocalDate.parse("2026-06-01"));
        invite.setFirstSeenAt(firstSeen);
        invite.setLastSeenAt(lastSeen);
        invite.setActive(false);

        assertThat(invite.getId()).isEqualTo(1L);
        assertThat(invite.getGuild()).isSameAs(guild);
        assertThat(invite.getCharacterName()).isEqualTo("Invited Paladin");
        assertThat(invite.getInvitedAt()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(invite.getFirstSeenAt()).isEqualTo(firstSeen);
        assertThat(invite.getLastSeenAt()).isEqualTo(lastSeen);
        assertThat(invite.isActive()).isFalse();

        GuildSnapshot snapshot = new GuildSnapshot();
        Instant scrapedAt = Instant.parse("2026-06-02T12:00:00Z");
        snapshot.setId(2L);
        snapshot.setGuild(guild);
        snapshot.setScrapedAt(scrapedAt);
        snapshot.setMemberCount(33);
        snapshot.setOnlineCount(7);
        snapshot.setRawHash("abc123");

        assertThat(snapshot.getId()).isEqualTo(2L);
        assertThat(snapshot.getGuild()).isSameAs(guild);
        assertThat(snapshot.getScrapedAt()).isEqualTo(scrapedAt);
        assertThat(snapshot.getMemberCount()).isEqualTo(33);
        assertThat(snapshot.getOnlineCount()).isEqualTo(7);
        assertThat(snapshot.getRawHash()).isEqualTo("abc123");
    }

    @Test
    void guildCharacterDeathAndScrapeMaintainAssociations() {
        Guild guild = new Guild();
        CharacterEntity character = new CharacterEntity();
        World world = new World();
        ScrapePlayer player = new ScrapePlayer();
        Instant now = Instant.parse("2026-06-02T12:30:00Z");

        GuildCharacter guildCharacter = new GuildCharacter();
        guildCharacter.setId(10L);
        guildCharacter.setGuild(guild);
        guildCharacter.setCharacter(character);
        guildCharacter.setCreatedAt(now);

        assertThat(guildCharacter.getId()).isEqualTo(10L);
        assertThat(guildCharacter.getGuild()).isSameAs(guild);
        assertThat(guildCharacter.getCharacter()).isSameAs(character);
        assertThat(guildCharacter.getCreatedAt()).isEqualTo(now);

        CharacterDeath death = new CharacterDeath();
        death.setId(20L);
        death.setCharacter(character);
        death.setDeathDate(now);
        death.setKilledBy("a dragon lord");

        assertThat(death.getId()).isEqualTo(20L);
        assertThat(death.getCharacter()).isSameAs(character);
        assertThat(death.getDeathDate()).isEqualTo(now);
        assertThat(death.getKilledBy()).isEqualTo("a dragon lord");

        Scrape scrape = new Scrape(30L, world, now, 42, "legacy-json");
        scrape.addPlayer(player);
        assertThat(scrape.getId()).isEqualTo(30L);
        assertThat(scrape.getWorld()).isSameAs(world);
        assertThat(scrape.getScrapeTime()).isEqualTo(now);
        assertThat(scrape.getPlayersOnline()).isEqualTo(42);
        assertThat(scrape.getPlayers()).containsExactly(player);
        assertThat(player.getScrape()).isSameAs(scrape);

        ScrapePlayer replacement = new ScrapePlayer();
        scrape.setPlayers(List.of(replacement));
        assertThat(scrape.getPlayers()).containsExactly(replacement);
        assertThat(replacement.getScrape()).isSameAs(scrape);
    }

    @Test
    void authPersistenceEntitiesExposeSecurityState() {
        Instant created = Instant.parse("2026-06-02T13:00:00Z");
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("bcrypt");
        user.setRoles("ADMIN,USER");
        user.setEnabled(false);
        user.setCreatedAt(created);

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getPassword()).isEqualTo("bcrypt");
        assertThat(user.getRoles()).isEqualTo("ADMIN,USER");
        assertThat(user.getEnabled()).isFalse();
        assertThat(user.getCreatedAt()).isEqualTo(created);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(2L);
        refreshToken.setUser(user);
        refreshToken.setToken("refresh-token");
        refreshToken.setExpiresAt(created.plusSeconds(3600));
        refreshToken.setCreatedAt(created);
        refreshToken.setRevoked(true);

        assertThat(refreshToken.getId()).isEqualTo(2L);
        assertThat(refreshToken.getUser()).isSameAs(user);
        assertThat(refreshToken.getToken()).isEqualTo("refresh-token");
        assertThat(refreshToken.getExpiresAt()).isEqualTo(created.plusSeconds(3600));
        assertThat(refreshToken.getCreatedAt()).isEqualTo(created);
        assertThat(refreshToken.getRevoked()).isTrue();

        BlacklistedToken blacklistedToken = new BlacklistedToken();
        blacklistedToken.setId(3L);
        blacklistedToken.setJti("jwt-id");
        blacklistedToken.setToken("access-token");
        blacklistedToken.setRevokedAt(created.plusSeconds(10));
        blacklistedToken.setReason("logout");

        assertThat(blacklistedToken.getId()).isEqualTo(3L);
        assertThat(blacklistedToken.getJti()).isEqualTo("jwt-id");
        assertThat(blacklistedToken.getToken()).isEqualTo("access-token");
        assertThat(blacklistedToken.getRevokedAt()).isEqualTo(created.plusSeconds(10));
        assertThat(blacklistedToken.getReason()).isEqualTo("logout");
    }
}
