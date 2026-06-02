package com.nathan.tibiastats.application.query;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.GuildMembership;
import com.nathan.tibiastats.domain.model.GuildMembershipEvent;
import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.CharacterRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildCatalogRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildMembershipEventRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildMembershipRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryFacadeCoverageTest {
    private static final Instant NOW = Instant.parse("2026-06-02T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-06-02");

    @Test
    void apiQueryServiceDelegatesToFocusedReadModels() {
        CharacterOnlineReadModelService characterOnline = mock(CharacterOnlineReadModelService.class);
        ScrapeJobReadModelService scrapeJobs = mock(ScrapeJobReadModelService.class);
        WorldReadModelService worlds = mock(WorldReadModelService.class);
        CharacterIdentityReadModelService characters = mock(CharacterIdentityReadModelService.class);
        LegacyHighscoreReadModelService legacyHighscores = mock(LegacyHighscoreReadModelService.class);
        ApiQueryService service = new ApiQueryService(characterOnline, scrapeJobs, worlds, characters, legacyHighscores);

        ApiQueryService.WorldView world = new ApiQueryService.WorldView(1, "Antica", "Open PvP", "EU", "1000", TODAY, "blocked", "regular", 42, NOW);
        ApiQueryService.CharacterView character = new ApiQueryService.CharacterView(10L, "Knight", 300, "male", "Elite Knight", "Elite Knight", 50, "Thais", null, "Premium Account", NOW, NOW, "UPDATED");
        ApiQueryService.CharacterNameView name = new ApiQueryService.CharacterNameView(20L, 10L, "Knight", true, null);
        ApiQueryService.HighscoreView highscore = new ApiQueryService.HighscoreView(30L, 1, "Knight", 10L, "Antica", StatCategory.EXPERIENCE.name(), 0, TODAY, 123456L, NOW);
        ApiQueryService.ScrapeJobView scrapeJob = new ApiQueryService.ScrapeJobView(40L, "worlds", "SUCCESS", NOW, NOW, 100L, 2, 1, 1, 0, null);
        ApiQueryService.CharacterOnlinePointView point = new ApiQueryService.CharacterOnlinePointView(10L, "Knight", 50L, "Antica", NOW, 42);
        ApiQueryService.CharacterOnlineSessionView session = new ApiQueryService.CharacterOnlineSessionView(10L, "Knight", "Antica", NOW, NOW.plusSeconds(600), 10L, 3);
        ApiQueryService.CharacterOnlineWorldSummaryView summary = new ApiQueryService.CharacterOnlineWorldSummaryView(10L, "Knight", "Antica", 2, 1, 10L, NOW, NOW.plusSeconds(600));

        when(worlds.findWorlds()).thenReturn(List.of(world));
        when(worlds.findWorld("Antica")).thenReturn(Optional.of(world));
        when(characters.findCharacter("Knight")).thenReturn(Optional.of(character));
        when(characters.findCharacterNames("Knight")).thenReturn(List.of(name));
        when(characters.findCharacterNames(10L)).thenReturn(List.of(name));
        when(legacyHighscores.findCharacterHighscores("Knight", StatCategory.EXPERIENCE, "Antica", 0, TODAY.minusDays(1), TODAY, 25)).thenReturn(List.of(highscore));
        when(legacyHighscores.findHighscores("Antica", StatCategory.EXPERIENCE, 0, TODAY, 25)).thenReturn(List.of(highscore));
        when(scrapeJobs.findScrapeJobs("worlds", "success", 10)).thenReturn(List.of(scrapeJob));
        when(characterOnline.findHistory("Knight", "Antica", NOW.minusSeconds(60), NOW, 25)).thenReturn(List.of(point));
        when(characterOnline.findSessions("Knight", "Antica", NOW.minusSeconds(60), NOW, 30, 25)).thenReturn(List.of(session));
        when(characterOnline.findWorldSummaries("Knight", "Antica", NOW.minusSeconds(60), NOW, 30)).thenReturn(List.of(summary));

        assertThat(service.findWorlds()).containsExactly(world);
        assertThat(service.findWorld("Antica")).contains(world);
        assertThat(service.findCharacter("Knight")).contains(character);
        assertThat(service.findCharacterNames("Knight")).containsExactly(name);
        assertThat(service.findCharacterNames(10L)).containsExactly(name);
        assertThat(service.findCharacterHighscores("Knight", StatCategory.EXPERIENCE, "Antica", 0, TODAY.minusDays(1), TODAY, 25)).containsExactly(highscore);
        assertThat(service.findHighscores("Antica", StatCategory.EXPERIENCE, 0, TODAY, 25)).containsExactly(highscore);
        assertThat(service.findScrapeJobs("worlds", "success", 10)).containsExactly(scrapeJob);
        assertThat(service.findCharacterOnlineHistory("Knight", "Antica", NOW.minusSeconds(60), NOW, 25)).containsExactly(point);
        assertThat(service.findCharacterOnlineSessions("Knight", "Antica", NOW.minusSeconds(60), NOW, 30, 25)).containsExactly(session);
        assertThat(service.findCharacterOnlineWorldSummaries("Knight", "Antica", NOW.minusSeconds(60), NOW, 30)).containsExactly(summary);
        assertThat(highscore.valueText()).isEqualTo("123456");
        assertThat(new ApiQueryService.HighscoreView(31L, null, "Knight", 10L, "Antica", "EXPERIENCE", 0, TODAY, null, NOW).valueText()).isNull();
    }

    @Test
    void highscoreApiQueryServiceDelegatesToExperienceAndRecordReadModels() {
        HighscoreExperienceReadModelService experience = mock(HighscoreExperienceReadModelService.class);
        HighscoreRecordReadModelService records = mock(HighscoreRecordReadModelService.class);
        HighscoreApiQueryService service = new HighscoreApiQueryService(experience, records);
        HighscoreApiQueryService.ExperienceDailyView daily = new HighscoreApiQueryService.ExperienceDailyView(TODAY, 1, "Knight", 10L, "Antica", 0, 1000L, 100, 0, NOW);
        HighscoreApiQueryService.ExperienceGainView gain = new HighscoreApiQueryService.ExperienceGainView("Knight", 10L, "Antica", TODAY.minusDays(1), TODAY, 1000L, 1500L, 500L, 2, 1, 0);
        HighscoreApiQueryService.CurrentHighscoreView current = new HighscoreApiQueryService.CurrentHighscoreView(1L, 1, "Knight", 10L, "Antica", StatCategory.MAGIC_LEVEL.name(), 11, 0, 120L, TODAY, TODAY, TODAY, NOW);
        HighscoreApiQueryService.PeriodHighscoreView period = new HighscoreApiQueryService.PeriodHighscoreView(2L, 1, "Knight", 10L, "Antica", StatCategory.MAGIC_LEVEL.name(), 11, 0, 120L, TODAY.minusDays(1), TODAY, NOW);

        when(experience.findExperienceDaily("Antica", TODAY, 0, 25)).thenReturn(List.of(daily));
        when(experience.findExperienceRanks("Antica", TODAY, 0, 25)).thenReturn(List.of(daily));
        when(experience.findExperienceGains("Antica", TODAY.minusDays(1), TODAY, 0, 25)).thenReturn(List.of(gain));
        when(experience.findCharacterExperienceDaily("Knight", "Antica", 0, TODAY.minusDays(1), TODAY, 25)).thenReturn(List.of(daily));
        when(records.findCurrent("Antica", StatCategory.MAGIC_LEVEL, 0, 25)).thenReturn(List.of(current));
        when(records.findHistory("Antica", StatCategory.MAGIC_LEVEL, "Knight", 0, TODAY.minusDays(1), TODAY, 25)).thenReturn(List.of(period));

        assertThat(service.findExperienceDaily("Antica", TODAY, 0, 25)).containsExactly(daily);
        assertThat(service.findExperienceRanks("Antica", TODAY, 0, 25)).containsExactly(daily);
        assertThat(service.findExperienceGains("Antica", TODAY.minusDays(1), TODAY, 0, 25)).containsExactly(gain);
        assertThat(service.findCharacterExperienceDaily("Knight", "Antica", 0, TODAY.minusDays(1), TODAY, 25)).containsExactly(daily);
        assertThat(service.findCurrent("Antica", StatCategory.MAGIC_LEVEL, 0, 25)).containsExactly(current);
        assertThat(service.findHistory("Antica", StatCategory.MAGIC_LEVEL, "Knight", 0, TODAY.minusDays(1), TODAY, 25)).containsExactly(period);
    }

    @Test
    void guildQueryServiceResolvesOptionalFiltersAndMapsViews() {
        GuildCatalogReadModelService catalog = mock(GuildCatalogReadModelService.class);
        GuildMembershipReadModelService memberships = mock(GuildMembershipReadModelService.class);
        GuildMembershipEventReadModelService events = mock(GuildMembershipEventReadModelService.class);
        GuildQueryService service = new GuildQueryService(catalog, memberships, events);
        Guild guild = guild(10L, "Raw Raw", "Antica");
        CharacterEntity character = character(20L);
        GuildMembership membership = membership(30L, guild, character, "Knight");
        GuildMembershipEvent event = membershipEvent(40L, character, null, guild, GuildMembershipEventType.JOINED, "Knight joined Raw Raw");

        when(catalog.findGuilds("Antica", true)).thenReturn(List.of(guild));
        when(catalog.findGuild("Raw Raw")).thenReturn(guild);
        when(catalog.findGuildId("Raw Raw")).thenReturn(10L);
        when(memberships.findMembers(10L, true)).thenReturn(List.of(membership));
        when(memberships.findCharacterId("Knight")).thenReturn(20L);
        when(events.findEvents(10L, 20L, GuildMembershipEventType.JOINED, NOW.minusSeconds(60), NOW, 25)).thenReturn(List.of(event));
        when(events.findEvents(null, null, null, null, null, 10)).thenReturn(List.of(event));
        when(memberships.findCharacterGuildHistory("Knight")).thenReturn(List.of(membership));

        assertThat(service.findGuilds("Antica", true))
                .extracting(GuildQueryViews.GuildView::name)
                .containsExactly("Raw Raw");
        assertThat(service.findGuild("Raw Raw").world()).isEqualTo("Antica");
        assertThat(service.findMembers("Raw Raw", true))
                .extracting(GuildQueryViews.GuildMemberView::characterName)
                .containsExactly("Knight");
        assertThat(service.findEvents("Raw Raw", "Knight", GuildMembershipEventType.JOINED, NOW.minusSeconds(60), NOW, 25))
                .extracting(GuildQueryViews.GuildMembershipEventView::description)
                .containsExactly("Knight joined Raw Raw");
        assertThat(service.findEvents(" ", " ", null, null, null, 10))
                .extracting(GuildQueryViews.GuildMembershipEventView::toGuildName)
                .containsExactly("Raw Raw");
        assertThat(service.findCharacterGuildHistory("Knight"))
                .extracting(GuildQueryViews.GuildMemberView::membershipId)
                .containsExactly(30L);
    }

    @Test
    void guildCatalogReadModelDelegatesAndRejectsMissingGuilds() {
        GuildCatalogRepositoryPort guilds = mock(GuildCatalogRepositoryPort.class);
        GuildCatalogReadModelService service = new GuildCatalogReadModelService(guilds);
        Guild guild = guild(10L, "Raw Raw", "Antica");
        when(guilds.findGuilds("Antica", true)).thenReturn(List.of(guild));
        when(guilds.findGuild("Raw Raw")).thenReturn(Optional.of(guild));
        when(guilds.findGuild("Missing")).thenReturn(Optional.empty());

        assertThat(service.findGuilds("Antica", true)).containsExactly(guild);
        assertThat(service.findGuild("Raw Raw")).isSameAs(guild);
        assertThat(service.findGuildId("Raw Raw")).isEqualTo(10L);
        assertThatThrownBy(() -> service.findGuild("Missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Guild not found: Missing");
    }

    @Test
    void guildMembershipReadModelDelegatesAndRejectsMissingCharacters() {
        GuildMembershipRepositoryPort guilds = mock(GuildMembershipRepositoryPort.class);
        CharacterRepositoryPort characters = mock(CharacterRepositoryPort.class);
        GuildMembershipReadModelService service = new GuildMembershipReadModelService(guilds, characters);
        Guild guild = guild(10L, "Raw Raw", "Antica");
        CharacterEntity character = character(20L);
        GuildMembership membership = membership(30L, guild, character, "Knight");
        when(guilds.findMemberships(10L, true)).thenReturn(List.of(membership));
        when(guilds.findMembershipHistory(20L)).thenReturn(List.of(membership));
        when(characters.findByAnyName("Knight", Instant.EPOCH)).thenReturn(Optional.of(character));
        when(characters.findByAnyName("Missing", Instant.EPOCH)).thenReturn(Optional.empty());

        assertThat(service.findMembers(10L, true)).containsExactly(membership);
        assertThat(service.findCharacterId("Knight")).isEqualTo(20L);
        assertThat(service.findCharacterGuildHistory("Knight")).containsExactly(membership);
        assertThatThrownBy(() -> service.findCharacterId("Missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Character not found: Missing");
    }

    @Test
    void guildMembershipEventReadModelDelegatesFilters() {
        GuildMembershipEventRepositoryPort guilds = mock(GuildMembershipEventRepositoryPort.class);
        GuildMembershipEventReadModelService service = new GuildMembershipEventReadModelService(guilds);
        Guild guild = guild(10L, "Raw Raw", "Antica");
        CharacterEntity character = character(20L);
        GuildMembershipEvent event = membershipEvent(40L, character, null, guild, GuildMembershipEventType.JOINED, "joined");
        when(guilds.findEvents(10L, 20L, GuildMembershipEventType.JOINED, NOW.minusSeconds(60), NOW, 25)).thenReturn(List.of(event));

        assertThat(service.findEvents(10L, 20L, GuildMembershipEventType.JOINED, NOW.minusSeconds(60), NOW, 25)).containsExactly(event);
        verify(guilds).findEvents(10L, 20L, GuildMembershipEventType.JOINED, NOW.minusSeconds(60), NOW, 25);
    }

    private Guild guild(Long id, String name, String worldName) {
        World world = new World(worldName, "Open PvP", "EU");
        world.setId(1);
        Guild guild = new Guild();
        guild.setId(id);
        guild.setName(name);
        guild.setNormalizedName(name.toLowerCase());
        guild.setWorld(world);
        guild.setDescription("Neutral guild");
        guild.setHomepage("https://guild.example");
        guild.setLogoUrl("https://static.tibia.com/guildlogo.gif");
        guild.setFoundedAt(TODAY);
        guild.setActive(true);
        guild.setLastSeenAt(NOW);
        guild.setLastScrapedAt(NOW.plusSeconds(10));
        return guild;
    }

    private CharacterEntity character(Long id) {
        CharacterEntity character = new CharacterEntity();
        character.setId(id);
        character.setLevel(300);
        return character;
    }

    private GuildMembership membership(Long id, Guild guild, CharacterEntity character, String name) {
        GuildMembership membership = new GuildMembership();
        membership.setId(id);
        membership.setGuild(guild);
        membership.setCharacter(character);
        membership.setCharacterNameSnapshot(name);
        membership.setRankName("Leader");
        membership.setTitle("Boss caller");
        membership.setVocation("Elite Knight");
        membership.setLevel(300);
        membership.setJoinedAt(NOW.minusSeconds(3600));
        membership.setJoinedOn(TODAY.minusDays(1));
        membership.setFirstSeenAt(NOW.minusSeconds(3600));
        membership.setLastSeenAt(NOW);
        membership.setActive(true);
        return membership;
    }

    private GuildMembershipEvent membershipEvent(Long id,
                                                 CharacterEntity character,
                                                 Guild fromGuild,
                                                 Guild toGuild,
                                                 GuildMembershipEventType type,
                                                 String description) {
        GuildMembershipEvent event = new GuildMembershipEvent();
        event.setId(id);
        event.setCharacter(character);
        event.setCharacterNameSnapshot("Knight");
        event.setFromGuild(fromGuild);
        event.setToGuild(toGuild);
        event.setEventType(type);
        event.setObservedAt(NOW);
        event.setDescription(description);
        return event;
    }
}
