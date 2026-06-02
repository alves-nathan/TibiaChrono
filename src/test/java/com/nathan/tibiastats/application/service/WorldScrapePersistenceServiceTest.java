package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.model.Scrape;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldScrapePersistenceServiceTest {
    @Test
    void saveWorldScrapeUpdatesExistingWorldAndPersistsOnlyNamedPlayers() {
        WorldRepositoryPort repository = mock(WorldRepositoryPort.class);
        OnlineCharacterSnapshotService onlineCharacters = mock(OnlineCharacterSnapshotService.class);
        WorldScrapePersistenceService service = new WorldScrapePersistenceService(
                repository,
                onlineCharacters,
                new ImmediateTransactionTemplate()
        );
        World world = new World("Antica", "Open PvP", "EU");
        world.setId(1);
        world.setTransferType("regular");
        world.setGameWorldType("regular");
        CharacterEntity knight = character(10L);
        when(repository.findByName("Antica")).thenReturn(Optional.of(world));
        when(repository.save(any(World.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveScrape(any(Scrape.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(onlineCharacters.resolveAndUpdate(new WorldScrapeSnapshot.OnlineCharacter("Knight One", 300, "Elite Knight")))
                .thenReturn(knight);
        WorldScrapeSnapshot.Page page = new WorldScrapeSnapshot.Page(
                new WorldScrapeSnapshot.Target("Antica", "Optional PvP", " ", 42, "locked", "premium"),
                "Antica",
                42,
                java.util.Arrays.asList(
                        new WorldScrapeSnapshot.OnlineCharacter("Knight One", 300, "Elite Knight"),
                        new WorldScrapeSnapshot.OnlineCharacter(" ", 100, "Druid"),
                        null
                ),
                "Record: 1000",
                LocalDate.parse("1997-01-01"),
                " transferable ",
                " regular "
        );

        service.saveWorldScrape(page);

        assertThat(world.getPvpType()).isEqualTo("Optional PvP");
        assertThat(world.getLocation()).isEqualTo("EU");
        assertThat(world.getOnlineRecord()).isEqualTo("Record: 1000");
        assertThat(world.getCreationDate()).isEqualTo(LocalDate.parse("1997-01-01"));
        assertThat(world.getTransferType()).isEqualTo("transferable");
        assertThat(world.getGameWorldType()).isEqualTo("regular");
        ArgumentCaptor<Scrape> scrapeCaptor = ArgumentCaptor.forClass(Scrape.class);
        verify(repository).saveScrape(scrapeCaptor.capture());
        Scrape scrape = scrapeCaptor.getValue();
        assertThat(scrape.getWorld()).isSameAs(world);
        assertThat(scrape.getPlayersOnline()).isEqualTo(42);
        assertThat(scrape.getScrapeTime()).isNotNull();
        assertThat(scrape.getPlayers()).hasSize(1);
        assertThat(scrape.getPlayers().getFirst().getCharacter()).isSameAs(knight);
        assertThat(scrape.getPlayers().getFirst().getScrape()).isSameAs(scrape);
    }

    @Test
    void saveWorldScrapeCreatesWorldFromTargetWhenMissing() {
        WorldRepositoryPort repository = mock(WorldRepositoryPort.class);
        OnlineCharacterSnapshotService onlineCharacters = mock(OnlineCharacterSnapshotService.class);
        WorldScrapePersistenceService service = new WorldScrapePersistenceService(
                repository,
                onlineCharacters,
                new ImmediateTransactionTemplate()
        );
        when(repository.findByName("Bona")).thenReturn(Optional.empty());
        when(repository.save(any(World.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveScrape(any(Scrape.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WorldScrapeSnapshot.Page page = new WorldScrapeSnapshot.Page(
                new WorldScrapeSnapshot.Target("Bona", "Optional PvP", "NA", 7, "blocked", "experimental"),
                "Bona",
                7,
                List.of(),
                null,
                null,
                null,
                null
        );

        service.saveWorldScrape(page);

        ArgumentCaptor<World> worldCaptor = ArgumentCaptor.forClass(World.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(worldCaptor.capture());
        World created = worldCaptor.getAllValues().getFirst();
        assertThat(created.getName()).isEqualTo("Bona");
        assertThat(created.getPvpType()).isEqualTo("Optional PvP");
        assertThat(created.getLocation()).isEqualTo("NA");
        assertThat(created.getTransferType()).isEqualTo("blocked");
        assertThat(created.getGameWorldType()).isEqualTo("experimental");
    }

    private CharacterEntity character(Long id) {
        CharacterEntity character = new CharacterEntity();
        character.setId(id);
        return character;
    }

    private static final class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }

        @Override
        public void executeWithoutResult(Consumer<TransactionStatus> action) {
            action.accept(null);
        }
    }
}
