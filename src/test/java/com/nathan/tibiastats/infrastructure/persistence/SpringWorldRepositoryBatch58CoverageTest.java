package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.ScrapePlayer;
import com.nathan.tibiastats.domain.model.World;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringWorldRepositoryBatch58CoverageTest {
    @Test
    void springWorldRepositoryDelegatesBasicOperationsAndRangeQueries() {
        WorldJpa worlds = mock(WorldJpa.class);
        ScrapeJpa scrapes = mock(ScrapeJpa.class);
        ScrapePlayerJpa scrapePlayers = mock(ScrapePlayerJpa.class);
        SpringWorldRepository repository = new SpringWorldRepository(worlds, scrapes, scrapePlayers);
        World antica = new World("Antica", "Open PvP", "EU");
        Scrape scrape = new Scrape(antica, Instant.parse("2026-06-05T12:00:00Z"), 300, null);
        ScrapePlayer player = new ScrapePlayer(scrape, null);

        when(worlds.findByName("Antica")).thenReturn(Optional.of(antica));
        when(worlds.save(antica)).thenReturn(antica);
        when(worlds.findAll()).thenReturn(List.of(antica));
        when(scrapes.save(scrape)).thenReturn(scrape);
        when(scrapes.findRange(antica, Instant.parse("2026-06-05T10:00:00Z"), Instant.parse("2026-06-05T12:00:00Z"))).thenReturn(List.of(scrape));
        when(scrapePlayers.save(player)).thenReturn(player);

        assertThat(repository.findByName("Antica")).contains(antica);
        assertThat(repository.save(antica)).isSameAs(antica);
        assertThat(repository.findAll()).containsExactly(antica);
        assertThat(repository.saveScrape(scrape)).isSameAs(scrape);
        assertThat(repository.findScrapesByWorldAndRange(antica, Instant.parse("2026-06-05T10:00:00Z"), Instant.parse("2026-06-05T12:00:00Z"))).containsExactly(scrape);
        assertThat(repository.saveScrapePlayer(player)).isSameAs(player);
    }

    @Test
    void springWorldRepositoryMapsLatestScrapePresentAndEmptyResults() {
        WorldJpa worlds = mock(WorldJpa.class);
        ScrapeJpa scrapes = mock(ScrapeJpa.class);
        SpringWorldRepository repository = new SpringWorldRepository(worlds, scrapes, mock(ScrapePlayerJpa.class));
        World antica = new World("Antica", "Open PvP", "EU");
        Scrape scrape = new Scrape(antica, Instant.parse("2026-06-05T12:00:00Z"), 300, null);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        when(scrapes.findLatest(same(antica), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(scrape), List.of());

        assertThat(repository.findLatestByWorld(antica)).contains(scrape);
        assertThat(repository.findLatestByWorld(antica)).isEmpty();

        verify(scrapes, org.mockito.Mockito.times(2)).findLatest(same(antica), pageable.capture());
        assertThat(pageable.getAllValues()).allSatisfy(value -> {
            assertThat(value.getPageNumber()).isZero();
            assertThat(value.getPageSize()).isEqualTo(1);
        });
    }
}
