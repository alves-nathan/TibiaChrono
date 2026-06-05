package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.GuildInvite;
import com.nathan.tibiastats.domain.model.GuildMembership;
import com.nathan.tibiastats.domain.model.GuildMembershipEvent;
import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
import com.nathan.tibiastats.domain.model.GuildSnapshot;
import com.nathan.tibiastats.domain.model.Vocation;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildCatalogRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildInviteRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildMembershipEventRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildMembershipRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildScrapePort;
import com.nathan.tibiastats.domain.port.GuildSnapshotRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildDetailScrapeServiceTailCoverageTest {
    private static final Instant NOW = Instant.parse("2026-06-05T12:00:00Z");

    private GuildScrapePort scraper;
    private GuildCatalogRepositoryPort guilds;
    private GuildSnapshotRepositoryPort snapshots;
    private GuildMembershipRepositoryPort memberships;
    private GuildMembershipEventRepositoryPort membershipEvents;
    private GuildInviteRepositoryPort invites;
    private GuildCatalogService catalog;
    private CharacterNamingService characterNamingService;
    private CharacterRepositoryPort characters;
    private Guild guild;
    private GuildDetailScrapeService service;

    @BeforeEach
    void setUp() {
        scraper = mock(GuildScrapePort.class);
        guilds = mock(GuildCatalogRepositoryPort.class);
        snapshots = mock(GuildSnapshotRepositoryPort.class);
        memberships = mock(GuildMembershipRepositoryPort.class);
        membershipEvents = mock(GuildMembershipEventRepositoryPort.class);
        invites = mock(GuildInviteRepositoryPort.class);
        catalog = mock(GuildCatalogService.class);
        characterNamingService = mock(CharacterNamingService.class);
        characters = mock(CharacterRepositoryPort.class);
        guild = guild(10L, "Raw Raw");

        when(catalog.upsertGuild(anyString(), anyString(), any(), any(), any(), any(), anyBoolean(), any(Instant.class)))
                .thenReturn(new GuildCatalogService.GuildUpdate(guild, false));
        when(guilds.saveGuild(any(Guild.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshots.saveSnapshot(any(GuildSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberships.findActiveMemberships(any(Guild.class))).thenReturn(List.of());
        when(memberships.findActiveMembershipForCharacter(anyLong())).thenReturn(Optional.empty());
        when(memberships.saveMembership(any(GuildMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberships.saveAndFlushMembership(any(GuildMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipEvents.saveEvent(any(GuildMembershipEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invites.findActiveInvite(anyLong(), anyString())).thenReturn(Optional.empty());
        when(invites.findActiveInvites(anyLong())).thenReturn(List.of());
        when(invites.saveInvite(any(GuildInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(characters.findVocationByNameOrPromotionName(anyString())).thenReturn(Optional.empty());
        when(characters.save(any(CharacterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service = new GuildDetailScrapeService(
                scraper,
                guilds,
                snapshots,
                memberships,
                membershipEvents,
                invites,
                catalog,
                characterNamingService,
                characters,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void updateGuildDetailRejectsBlankGuildNameFromScraper() {
        when(scraper.fetchGuildDetail("Broken Guild")).thenReturn(new GuildScrapePort.GuildDetail(
                " ",
                "Antica",
                null,
                null,
                null,
                null,
                0,
                0,
                "blank-name",
                List.of(),
                List.of()
        ));

        assertThatThrownBy(() -> service.updateGuildDetail("Broken Guild"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Guild detail did not contain a guild name");
    }

    @Test
    void updateGuildDetailRefreshesActiveMembershipAndClosesMissingMembership() {
        CharacterEntity activeCharacter = character(1L);
        CharacterEntity leavingCharacter = character(2L);
        Vocation vocation = new Vocation();
        vocation.setId(4);
        vocation.setName("Paladin");

        GuildMembership active = activeMembership(guild, activeCharacter, "Active Name");
        active.setLeftAt(NOW.minusSeconds(60));
        GuildMembership duplicateActive = activeMembership(guild, activeCharacter, "Duplicate Active Name");
        GuildMembership ignoredNullCharacter = activeMembership(guild, null, "Ignored Name");
        GuildMembership leaving = activeMembership(guild, leavingCharacter, null);

        when(scraper.fetchGuildDetail("Raw Raw")).thenReturn(detail(
                List.of(
                        member("  ", "Member", null, "Knight", 10, null),
                        member(" Active\u00a0Name ", " ", " ", "Royal Paladin", 250, LocalDate.of(2026, 1, 2))
                ),
                null
        ));
        when(memberships.findActiveMemberships(guild)).thenReturn(List.of(active, duplicateActive, ignoredNullCharacter, leaving));
        when(characterNamingService.resolveObservedName(" Active\u00a0Name ")).thenReturn(activeCharacter);
        when(characters.findVocationByNameOrPromotionName("Royal Paladin")).thenReturn(Optional.of(vocation));

        GuildScrapeService.GuildDetailResult result = service.updateGuildDetail("Raw Raw");

        assertThat(result.membersSeen()).isEqualTo(2);
        assertThat(result.membershipsOpened()).isZero();
        assertThat(result.membershipsUpdated()).isEqualTo(1);
        assertThat(result.membershipsClosed()).isEqualTo(1);
        assertThat(result.transfers()).isZero();

        assertThat(active.getCharacterNameSnapshot()).isEqualTo("Active Name");
        assertThat(active.getRankName()).isNull();
        assertThat(active.getTitle()).isNull();
        assertThat(active.getVocation()).isEqualTo("Royal Paladin");
        assertThat(active.getLevel()).isEqualTo(250);
        assertThat(active.getJoinedOn()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(active.getJoinedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(active.getLastSeenAt()).isEqualTo(NOW);
        assertThat(active.getLeftAt()).isNull();
        assertThat(activeCharacter.getVocation()).isSameAs(vocation);

        assertThat(leaving.isActive()).isFalse();
        assertThat(leaving.getLeftAt()).isEqualTo(NOW);
        verify(membershipEvents).saveEvent(argThat(event ->
                event.getEventType() == GuildMembershipEventType.LEFT
                        && event.getFromGuild() == guild
                        && event.getToGuild() == null
                        && event.getCharacterNameSnapshot().isEmpty()
        ));
        verify(memberships, atLeastOnce()).saveMembership(active);
        verify(memberships, atLeastOnce()).saveMembership(leaving);
    }

    @Test
    void updateGuildDetailRefreshesRepositoryActiveMembershipFromSameGuild() {
        CharacterEntity character = character(3L);
        GuildMembership active = activeMembership(guild, character, "Same Guild");

        when(scraper.fetchGuildDetail("Raw Raw")).thenReturn(detail(
                List.of(member("Same Guild", "Vice Leader", "Recruiter", "Elder Druid", 350, LocalDate.of(2025, 12, 31))),
                List.of()
        ));
        when(characterNamingService.resolveObservedName("Same Guild")).thenReturn(character);
        when(memberships.findActiveMembershipForCharacter(3L)).thenReturn(Optional.of(active));

        GuildScrapeService.GuildDetailResult result = service.updateGuildDetail("Raw Raw");

        assertThat(result.membershipsUpdated()).isEqualTo(1);
        assertThat(active.getRankName()).isEqualTo("Vice Leader");
        assertThat(active.getTitle()).isEqualTo("Recruiter");
        assertThat(active.getJoinedAt()).isEqualTo(Instant.parse("2025-12-31T00:00:00Z"));
        assertThat(active.isActive()).isTrue();
        verify(memberships).saveMembership(active);
    }

    @Test
    void updateGuildDetailTransfersMembershipFromUnknownGuild() {
        CharacterEntity character = character(4L);
        GuildMembership previous = activeMembership(null, character, "Traveler");

        when(scraper.fetchGuildDetail("Raw Raw")).thenReturn(detail(
                List.of(member("Traveler", "Member", null, null, 120, null)),
                List.of()
        ));
        when(characterNamingService.resolveObservedName("Traveler")).thenReturn(character);
        when(memberships.findActiveMembershipForCharacter(4L)).thenReturn(Optional.of(previous));

        GuildScrapeService.GuildDetailResult result = service.updateGuildDetail("Raw Raw");

        assertThat(result.membershipsOpened()).isEqualTo(1);
        assertThat(result.membershipsClosed()).isEqualTo(1);
        assertThat(result.transfers()).isEqualTo(1);
        assertThat(previous.isActive()).isFalse();
        assertThat(previous.getLeftAt()).isEqualTo(NOW);

        verify(memberships).saveAndFlushMembership(previous);
        verify(membershipEvents).saveEvent(argThat(event ->
                event.getEventType() == GuildMembershipEventType.TRANSFERRED
                        && event.getFromGuild() == null
                        && event.getToGuild() == guild
                        && event.getDescription().contains("Unknown")
        ));
    }

    @Test
    void updateGuildDetailCreatesRefreshesAndClosesInvites() {
        GuildInvite existing = invite(20L, guild, "Existing Invite");
        existing.setFirstSeenAt(NOW.minusSeconds(120));
        GuildInvite stale = invite(21L, guild, "Old Invite");

        when(scraper.fetchGuildDetail("Raw Raw")).thenReturn(detail(
                null,
                List.of(
                        new GuildScrapePort.Invite(" ", LocalDate.of(2026, 1, 1)),
                        new GuildScrapePort.Invite("New\u00a0Invite", LocalDate.of(2026, 2, 3)),
                        new GuildScrapePort.Invite("Existing Invite", LocalDate.of(2026, 3, 4))
                )
        ));
        when(invites.findActiveInvite(10L, "Existing Invite")).thenReturn(Optional.of(existing));
        when(invites.findActiveInvites(10L)).thenReturn(List.of(existing, stale));

        GuildScrapeService.GuildDetailResult result = service.updateGuildDetail("Raw Raw");

        assertThat(result.membersSeen()).isZero();

        ArgumentCaptor<GuildInvite> savedInvites = ArgumentCaptor.forClass(GuildInvite.class);
        verify(invites, times(3)).saveInvite(savedInvites.capture());

        assertThat(savedInvites.getAllValues()).anySatisfy(invite -> {
            assertThat(invite.getGuild()).isSameAs(guild);
            assertThat(invite.getCharacterName()).isEqualTo("New Invite");
            assertThat(invite.getInvitedAt()).isEqualTo(LocalDate.of(2026, 2, 3));
            assertThat(invite.getFirstSeenAt()).isEqualTo(NOW);
            assertThat(invite.getLastSeenAt()).isEqualTo(NOW);
            assertThat(invite.isActive()).isTrue();
        });
        assertThat(existing.getLastSeenAt()).isEqualTo(NOW);
        assertThat(existing.isActive()).isTrue();
        assertThat(stale.isActive()).isFalse();
        assertThat(stale.getLastSeenAt()).isEqualTo(NOW);
    }

    private static GuildScrapePort.GuildDetail detail(
            List<GuildScrapePort.Member> members,
            List<GuildScrapePort.Invite> invites
    ) {
        return new GuildScrapePort.GuildDetail(
                "Raw Raw",
                "Antica",
                " Test guild ",
                "https://example.test",
                "https://example.test/logo.gif",
                LocalDate.of(2026, 5, 27),
                members == null ? null : members.size(),
                1,
                "hash",
                members,
                invites
        );
    }

    private static GuildScrapePort.Member member(
            String name,
            String rankName,
            String title,
            String vocation,
            Integer level,
            LocalDate joinedOn
    ) {
        return new GuildScrapePort.Member(name, rankName, title, vocation, level, joinedOn, false);
    }

    private static Guild guild(Long id, String name) {
        Guild guild = new Guild();
        guild.setId(id);
        guild.setName(name);
        guild.setNormalizedName(name.toLowerCase(java.util.Locale.ROOT));
        return guild;
    }

    private static CharacterEntity character(Long id) {
        CharacterEntity character = new CharacterEntity();
        character.setId(id);
        return character;
    }

    private static GuildMembership activeMembership(Guild guild, CharacterEntity character, String nameSnapshot) {
        GuildMembership membership = new GuildMembership();
        membership.setGuild(guild);
        membership.setCharacter(character);
        membership.setCharacterNameSnapshot(nameSnapshot);
        membership.setRankName("Member");
        membership.setTitle("Old Title");
        membership.setVocation("Knight");
        membership.setLevel(100);
        membership.setJoinedAt(NOW.minusSeconds(600));
        membership.setFirstSeenAt(NOW.minusSeconds(600));
        membership.setLastSeenAt(NOW.minusSeconds(300));
        membership.setActive(true);
        return membership;
    }

    private static GuildInvite invite(Long id, Guild guild, String characterName) {
        GuildInvite invite = new GuildInvite();
        invite.setId(id);
        invite.setGuild(guild);
        invite.setCharacterName(characterName);
        invite.setInvitedAt(LocalDate.of(2026, 1, 1));
        invite.setFirstSeenAt(NOW.minusSeconds(600));
        invite.setLastSeenAt(NOW.minusSeconds(300));
        invite.setActive(true);
        return invite;
    }
}
