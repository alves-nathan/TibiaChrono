package com.nathan.tibiastats.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEntityLifecycleTest {
    @Test
    void characterNameFactoriesNormalizeNamesAndSupportLifecycleTransitions() {
        CharacterEntity character = new CharacterEntity();
        Instant inactiveDate = Instant.parse("2026-01-01T00:00:00Z");

        CharacterName activeName = CharacterName.createActive("  Knight\u00A0Hero (traded) ", character);

        assertThat(activeName.getName()).isEqualTo("Knight Hero");
        assertThat(activeName.getCharacter()).isSameAs(character);
        assertThat(activeName.getActive()).isTrue();
        assertThat(activeName.isActive()).isTrue();
        assertThat(activeName.getInactiveDate()).isNull();

        activeName.deactivate(inactiveDate);

        assertThat(activeName.getActive()).isFalse();
        assertThat(activeName.isActive()).isFalse();
        assertThat(activeName.getInactiveDate()).isEqualTo(inactiveDate);

        activeName.activate();

        assertThat(activeName.getActive()).isTrue();
        assertThat(activeName.getInactiveDate()).isNull();

        CharacterName inactiveName = CharacterName.createInactive(" Old Mage (traded) ", character, inactiveDate);

        assertThat(inactiveName.getName()).isEqualTo("Old Mage");
        assertThat(inactiveName.getCharacter()).isSameAs(character);
        assertThat(inactiveName.getActive()).isFalse();
        assertThat(inactiveName.getInactiveDate()).isEqualTo(inactiveDate);
    }

    @Test
    void characterEntityAddsNamesWithBidirectionalAssociationAndStoresDetails() {
        CharacterEntity character = new CharacterEntity();
        Vocation vocation = new Vocation();
        vocation.setId(4);
        vocation.setName("Knight");
        vocation.setPromotionName("Elite Knight");
        Instant createdAt = Instant.parse("2020-01-01T00:00:00Z");
        Instant scrapedAt = Instant.parse("2026-01-02T03:04:05Z");
        OffsetDateTime lastLogin = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);
        CharacterName name = new CharacterName();
        name.setName("  Knight Hero  ");

        character.setId(10L);
        character.setSex(CharacterEntity.Sex.male);
        character.setVocation(vocation);
        character.setLevel(250);
        character.setAchievementPoints(123);
        character.setResidence("Thais");
        character.setLastLogin(lastLogin);
        character.setAccStatus("Premium Account");
        character.setCreationDate(createdAt);
        character.setDetailsLastScrapedAt(scrapedAt);
        character.setDetailsLastScrapeStatus("SUCCESS");
        character.setDetailsLastScrapeError("none");
        character.addName(name);

        assertThat(character.getId()).isEqualTo(10L);
        assertThat(character.getSex()).isEqualTo(CharacterEntity.Sex.male);
        assertThat(character.getVocation()).isSameAs(vocation);
        assertThat(character.getLevel()).isEqualTo(250);
        assertThat(character.getAchievementPoints()).isEqualTo(123);
        assertThat(character.getResidence()).isEqualTo("Thais");
        assertThat(character.getLastLogin()).isEqualTo(lastLogin);
        assertThat(character.getAccStatus()).isEqualTo("Premium Account");
        assertThat(character.getCreationDate()).isEqualTo(createdAt);
        assertThat(character.getDetailsLastScrapedAt()).isEqualTo(scrapedAt);
        assertThat(character.getDetailsLastScrapeStatus()).isEqualTo("SUCCESS");
        assertThat(character.getDetailsLastScrapeError()).isEqualTo("none");
        assertThat(character.getNames()).containsExactly(name);
        assertThat(name.getCharacter()).isSameAs(character);
    }

    @Test
    void characterWorldCreatesActiveLinksAndCanDeactivateThem() {
        World world = new World("Antica", "Open PvP", "EU");
        world.setId(1);
        world.setOnlineRecord("1,234 players (on Jan 01 2026)");
        world.setCreationDate(LocalDate.parse("1997-01-01"));
        world.setTransferType("blocked");
        world.setGameWorldType("regular");
        CharacterEntity character = new CharacterEntity();
        CharacterWorld characterWorld = CharacterWorld.createActive(world);
        Instant inactiveAt = Instant.parse("2026-01-03T00:00:00Z");

        characterWorld.setId(99L);
        characterWorld.setCharacter(character);

        assertThat(characterWorld.getId()).isEqualTo(99L);
        assertThat(characterWorld.getWorld()).isSameAs(world);
        assertThat(characterWorld.getCharacter()).isSameAs(character);
        assertThat(characterWorld.getActive()).isTrue();
        assertThat(characterWorld.getInactiveDate()).isNull();
        assertThat(world.getName()).isEqualTo("Antica");
        assertThat(world.getPvpType()).isEqualTo("Open PvP");
        assertThat(world.getLocation()).isEqualTo("EU");
        assertThat(world.getOnlineRecord()).contains("1,234");
        assertThat(world.getCreationDate()).isEqualTo(LocalDate.parse("1997-01-01"));
        assertThat(world.getTransferType()).isEqualTo("blocked");
        assertThat(world.getGameWorldType()).isEqualTo("regular");

        characterWorld.deactivate(inactiveAt);

        assertThat(characterWorld.getActive()).isFalse();
        assertThat(characterWorld.getInactiveDate()).isEqualTo(inactiveAt);
    }

    @Test
    void scrapeMaintainsPlayerBackReferencesWhenAddingOrReplacingPlayers() {
        World world = new World("Antica", "Open PvP", "EU");
        Instant scrapedAt = Instant.parse("2026-01-04T00:00:00Z");
        Scrape scrape = new Scrape(world, scrapedAt, 2, null);
        ScrapePlayer first = new ScrapePlayer();
        ScrapePlayer second = new ScrapePlayer(null, new CharacterEntity());

        scrape.setId(11L);
        scrape.addPlayer(first);

        assertThat(scrape.getId()).isEqualTo(11L);
        assertThat(scrape.getWorld()).isSameAs(world);
        assertThat(scrape.getScrapeTime()).isEqualTo(scrapedAt);
        assertThat(scrape.getPlayersOnline()).isEqualTo(2);
        assertThat(scrape.getPlayers()).containsExactly(first);
        assertThat(first.getScrape()).isSameAs(scrape);

        scrape.setPlayers(List.of(second));

        assertThat(scrape.getPlayers()).containsExactly(second);
        assertThat(second.getScrape()).isSameAs(scrape);

        scrape.setPlayers(null);

        assertThat(scrape.getPlayers()).isEmpty();
    }

    @Test
    void characterStatRecordDefaultsNullVocationFilterToZero() {
        CharacterStatRecord record = new CharacterStatRecord();
        CharacterEntity character = new CharacterEntity();
        World world = new World("Antica", "Open PvP", "EU");
        Instant scrapedAt = Instant.parse("2026-01-05T00:00:00Z");
        LocalDate date = LocalDate.parse("2026-01-05");

        record.setId(1L);
        record.setCharacter(character);
        record.setCategory(StatCategory.EXPERIENCE);
        record.setVocationFilterId(null);
        record.setDate(date);
        record.setValue(123_456L);
        record.setRank(7);
        record.setWorld(world);
        record.setScrapedAt(scrapedAt);

        assertThat(record.getId()).isEqualTo(1L);
        assertThat(record.getCharacter()).isSameAs(character);
        assertThat(record.getCategory()).isEqualTo(StatCategory.EXPERIENCE);
        assertThat(record.getVocationFilterId()).isZero();
        assertThat(record.getDate()).isEqualTo(date);
        assertThat(record.getValue()).isEqualTo(123_456L);
        assertThat(record.getRank()).isEqualTo(7);
        assertThat(record.getWorld()).isSameAs(world);
        assertThat(record.getScrapedAt()).isEqualTo(scrapedAt);

        record.setVocationFilterId(4);

        assertThat(record.getVocationFilterId()).isEqualTo(4);
    }

    @Test
    void scrapeJobExecutionStoresLifecycleCountersAndErrorDetails() {
        ScrapeJobExecution job = new ScrapeJobExecution();
        Instant startedAt = Instant.parse("2026-01-06T00:00:00Z");
        Instant finishedAt = Instant.parse("2026-01-06T00:01:00Z");

        job.setId(5L);
        job.setJobName("WORLD_SCRAPER");
        job.setStatus("FAILED");
        job.setStartedAt(startedAt);
        job.setFinishedAt(finishedAt);
        job.setDurationMs(60_000L);
        job.setItemsProcessed(10);
        job.setItemsCreated(3);
        job.setItemsUpdated(6);
        job.setItemsFailed(1);
        job.setErrorMessage("Network timeout");

        assertThat(job.getId()).isEqualTo(5L);
        assertThat(job.getJobName()).isEqualTo("WORLD_SCRAPER");
        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getStartedAt()).isEqualTo(startedAt);
        assertThat(job.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(job.getDurationMs()).isEqualTo(60_000L);
        assertThat(job.getItemsProcessed()).isEqualTo(10);
        assertThat(job.getItemsCreated()).isEqualTo(3);
        assertThat(job.getItemsUpdated()).isEqualTo(6);
        assertThat(job.getItemsFailed()).isEqualTo(1);
        assertThat(job.getErrorMessage()).isEqualTo("Network timeout");
    }
}
