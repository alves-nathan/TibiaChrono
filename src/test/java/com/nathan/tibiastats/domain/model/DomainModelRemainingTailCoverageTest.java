package com.nathan.tibiastats.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DomainModelRemainingTailCoverageTest {
    @Test
    void characterEntityCanReplaceNameAndWorldCollections() {
        CharacterEntity character = new CharacterEntity();
        Set<CharacterName> names = new HashSet<>();
        Set<CharacterWorld> worlds = new HashSet<>();

        character.setNames(names);
        character.setWorlds(worlds);

        assertThat(character.getNames()).isSameAs(names);
        assertThat(character.getWorlds()).isSameAs(worlds);
        assertThat(CharacterName.inactiveHorizon()).isBefore(Instant.now());
    }

    @Test
    void guildStoresRemainingLifecycleFields() {
        Guild guild = new Guild();
        World world = new World("Antica", "Open PvP", "EU");
        Instant seen = Instant.parse("2026-06-05T12:00:00Z");
        Instant scraped = Instant.parse("2026-06-05T12:05:00Z");
        LocalDate founded = LocalDate.parse("2024-01-01");

        guild.setId(1L);
        guild.setName("Raw Raw");
        guild.setNormalizedName("raw raw");
        guild.setWorld(world);
        guild.setDescription("Brazilian guild");
        guild.setHomepage("https://guild.test");
        guild.setLogoUrl("https://guild.test/logo.gif");
        guild.setFoundedAt(founded);
        guild.setActive(false);
        guild.setDisbandCondition("manual");
        guild.setLastSeenAt(seen);
        guild.setLastScrapedAt(scraped);

        assertThat(guild.getId()).isEqualTo(1L);
        assertThat(guild.getName()).isEqualTo("Raw Raw");
        assertThat(guild.getNormalizedName()).isEqualTo("raw raw");
        assertThat(guild.getWorld()).isSameAs(world);
        assertThat(guild.getDescription()).isEqualTo("Brazilian guild");
        assertThat(guild.getHomepage()).isEqualTo("https://guild.test");
        assertThat(guild.getLogoUrl()).endsWith("logo.gif");
        assertThat(guild.getFoundedAt()).isEqualTo(founded);
        assertThat(guild.isActive()).isFalse();
        assertThat(guild.getDisbandCondition()).isEqualTo("manual");
        assertThat(guild.getLastSeenAt()).isEqualTo(seen);
        assertThat(guild.getLastScrapedAt()).isEqualTo(scraped);
    }

    @Test
    void scrapePlayerConstructorAndSettersExposeAssociations() {
        Scrape scrape = new Scrape();
        CharacterEntity character = new CharacterEntity();
        ScrapePlayer player = new ScrapePlayer(scrape, character);

        assertThat(player.getScrape()).isSameAs(scrape);
        assertThat(player.getCharacter()).isSameAs(character);

        Scrape otherScrape = new Scrape();
        CharacterEntity otherCharacter = new CharacterEntity();
        player.setId(10L);
        player.setScrape(otherScrape);
        player.setCharacter(otherCharacter);

        assertThat(player.getId()).isEqualTo(10L);
        assertThat(player.getScrape()).isSameAs(otherScrape);
        assertThat(player.getCharacter()).isSameAs(otherCharacter);
    }

    @Test
    void vocationStoresPromotionName() {
        Vocation vocation = new Vocation();

        vocation.setId(4);
        vocation.setName("Paladin");
        vocation.setPromotionName("Royal Paladin");

        assertThat(vocation.getId()).isEqualTo(4);
        assertThat(vocation.getName()).isEqualTo("Paladin");
        assertThat(vocation.getPromotionName()).isEqualTo("Royal Paladin");
    }
}
