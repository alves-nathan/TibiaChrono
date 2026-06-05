package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.GuildInvite;
import com.nathan.tibiastats.domain.model.GuildMembership;
import com.nathan.tibiastats.domain.model.GuildMembershipEvent;
import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
import com.nathan.tibiastats.domain.model.GuildSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringGuildRepositoryTailCoverageTest {
    @Test
    void findGuildNormalizesNameBeforeCaseInsensitiveFallback() {
        Fixture fixture = new Fixture();
        Guild guild = guild(10L, "Raw Raw");
        when(fixture.guilds.findByNormalizedName("raw raw")).thenReturn(Optional.empty());
        when(fixture.guilds.findByNameIgnoreCase("  Raw   Raw  ")).thenReturn(Optional.of(guild));

        assertThat(fixture.repository.findGuild("  Raw   Raw  ")).containsSame(guild);
        assertThat(SpringGuildRepository.normalizeGuildName(null)).isEmpty();
        assertThat(SpringGuildRepository.normalizeGuildName("  Raw   Raw  ")).isEqualTo("raw raw");

        verify(fixture.guilds).findByNormalizedName("raw raw");
        verify(fixture.guilds).findByNameIgnoreCase("  Raw   Raw  ");
    }

    @Test
    void delegatesCatalogAndSnapshotMethodsWithBlankFilterNormalizationAndLimitClamping() {
        Fixture fixture = new Fixture();
        Guild guild = guild(20L, "Raw Raw");
        GuildSnapshot snapshot = new GuildSnapshot();
        when(fixture.guilds.save(guild)).thenReturn(guild);
        when(fixture.guilds.findGuilds(null, true)).thenReturn(List.of(guild));
        when(fixture.guilds.findActiveForDetailsRefresh(PageRequest.of(0, 1))).thenReturn(List.of(guild));
        when(fixture.snapshots.save(snapshot)).thenReturn(snapshot);

        assertThat(fixture.repository.saveGuild(guild)).isSameAs(guild);
        assertThat(fixture.repository.findGuilds("   ", true)).containsExactly(guild);
        assertThat(fixture.repository.findActiveForDetailsRefresh(0)).containsExactly(guild);
        assertThat(fixture.repository.saveSnapshot(snapshot)).isSameAs(snapshot);
    }

    @Test
    void delegatesMembershipMethodsAndClampsHistoryLimits() {
        Fixture fixture = new Fixture();
        Guild guild = guild(30L, "Raw Raw");
        GuildMembership membership = new GuildMembership();
        when(fixture.memberships.save(membership)).thenReturn(membership);
        when(fixture.memberships.saveAndFlush(membership)).thenReturn(membership);
        when(fixture.memberships.findActiveByGuildId(30L)).thenReturn(List.of(membership));
        when(fixture.memberships.findActiveByCharacterId(40L)).thenReturn(Optional.of(membership));
        when(fixture.memberships.findByGuildId(30L, false)).thenReturn(List.of(membership));
        when(fixture.memberships.findByGuildId(31L, null)).thenReturn(List.of(membership));
        when(fixture.memberships.findHistoryByCharacterId(40L)).thenReturn(List.of(membership));

        assertThat(fixture.repository.saveMembership(membership)).isSameAs(membership);
        assertThat(fixture.repository.saveAndFlushMembership(membership)).isSameAs(membership);
        fixture.repository.flushMemberships();
        assertThat(fixture.repository.findActiveMemberships(guild)).containsExactly(membership);
        assertThat(fixture.repository.findActiveMembershipForCharacter(40L)).contains(membership);
        assertThat(fixture.repository.findMemberships(guild, false)).containsExactly(membership);
        assertThat(fixture.repository.findMemberships(31L, null)).containsExactly(membership);
        assertThat(fixture.repository.findMembershipHistory(40L)).containsExactly(membership);

        verify(fixture.memberships).flush();
    }

    @Test
    void delegatesEventAndInviteMethodsWithLimitClamping() {
        Fixture fixture = new Fixture();
        GuildMembershipEvent event = new GuildMembershipEvent();
        GuildInvite invite = new GuildInvite();
        Instant from = Instant.parse("2026-06-05T10:00:00Z");
        Instant to = Instant.parse("2026-06-05T12:00:00Z");
        when(fixture.events.save(event)).thenReturn(event);
        when(fixture.events.findEvents(1L, 2L, GuildMembershipEventType.TRANSFERRED, from, to, PageRequest.of(0, 1)))
                .thenReturn(List.of(event));
        when(fixture.invites.save(invite)).thenReturn(invite);
        when(fixture.invites.findActive(1L, "Knight One")).thenReturn(Optional.of(invite));
        when(fixture.invites.findActiveByGuildId(1L)).thenReturn(List.of(invite));

        assertThat(fixture.repository.saveEvent(event)).isSameAs(event);
        assertThat(fixture.repository.findEvents(1L, 2L, GuildMembershipEventType.TRANSFERRED, from, to, 0))
                .containsExactly(event);
        assertThat(fixture.repository.saveInvite(invite)).isSameAs(invite);
        assertThat(fixture.repository.findActiveInvite(1L, "Knight One")).contains(invite);
        assertThat(fixture.repository.findActiveInvites(1L)).containsExactly(invite);
    }

    private static Guild guild(Long id, String name) {
        Guild guild = new Guild();
        guild.setId(id);
        guild.setName(name);
        guild.setNormalizedName(SpringGuildRepository.normalizeGuildName(name));
        return guild;
    }

    private static final class Fixture {
        private final GuildJpa guilds = mock(GuildJpa.class);
        private final GuildSnapshotJpa snapshots = mock(GuildSnapshotJpa.class);
        private final GuildMembershipJpa memberships = mock(GuildMembershipJpa.class);
        private final GuildMembershipEventJpa events = mock(GuildMembershipEventJpa.class);
        private final GuildInviteJpa invites = mock(GuildInviteJpa.class);
        private final SpringGuildRepository repository =
                new SpringGuildRepository(guilds, snapshots, memberships, events, invites);
    }
}
