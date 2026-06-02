package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.ScrapePort;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldScrapeClientTest {
    @Test
    void fetchWorldsOverviewMapsScrapePortSummariesIntoTargets() {
        ScrapePort scrapePort = mock(ScrapePort.class);
        when(scrapePort.fetchWorldsOverview()).thenReturn(List.of(
                new ScrapePort.WorldSummary("Antica", "Open PvP", "EU", 123, "regular", "regular"),
                new ScrapePort.WorldSummary("Bona", "Optional PvP", "NA", 42, "blocked", "experimental")
        ));
        WorldScrapeClient client = new WorldScrapeClient(scrapePort);

        List<WorldScrapeSnapshot.Target> targets = client.fetchWorldsOverview();

        assertThat(targets).containsExactly(
                new WorldScrapeSnapshot.Target("Antica", "Open PvP", "EU", 123, "regular", "regular"),
                new WorldScrapeSnapshot.Target("Bona", "Optional PvP", "NA", 42, "blocked", "experimental")
        );
    }

    @Test
    void fetchWorldPageMapsTargetToDomainWorldAndMapsPlayers() {
        ScrapePort scrapePort = mock(ScrapePort.class);
        WorldScrapeSnapshot.Target target = new WorldScrapeSnapshot.Target(
                "Antica",
                "Open PvP",
                "EU",
                100,
                "regular",
                "regular"
        );
        LocalDate creationDate = LocalDate.parse("1997-01-01");
        when(scrapePort.fetchWorldPage(eq("Antica"), any(World.class))).thenAnswer(invocation -> {
            World world = invocation.getArgument(1);
            assertThat(world.getName()).isEqualTo("Antica");
            assertThat(world.getPvpType()).isEqualTo("Open PvP");
            assertThat(world.getLocation()).isEqualTo("EU");
            assertThat(world.getTransferType()).isEqualTo("regular");
            assertThat(world.getGameWorldType()).isEqualTo("regular");
            return new ScrapePort.WorldOnline(
                    "Antica",
                    2,
                    List.of(
                            new ScrapePort.OnlineCharacterSnapshot("Knight One", 300, "Elite Knight"),
                            new ScrapePort.OnlineCharacterSnapshot("Druid Two", 250, "Elder Druid")
                    ),
                    "Record: 1000",
                    creationDate,
                    "regular",
                    "regular"
            );
        });
        WorldScrapeClient client = new WorldScrapeClient(scrapePort);

        WorldScrapeSnapshot.Page page = client.fetchWorldPage(target);

        assertThat(page.target()).isSameAs(target);
        assertThat(page.world()).isEqualTo("Antica");
        assertThat(page.playersOnline()).isEqualTo(2);
        assertThat(page.onlineRecord()).isEqualTo("Record: 1000");
        assertThat(page.creationDate()).isEqualTo(creationDate);
        assertThat(page.transferType()).isEqualTo("regular");
        assertThat(page.gameWorldType()).isEqualTo("regular");
        assertThat(page.players()).containsExactly(
                new WorldScrapeSnapshot.OnlineCharacter("Knight One", 300, "Elite Knight"),
                new WorldScrapeSnapshot.OnlineCharacter("Druid Two", 250, "Elder Druid")
        );
        verify(scrapePort).fetchWorldPage(eq("Antica"), any(World.class));
    }

    @Test
    void fetchWorldPageTreatsNullPlayerListAsEmpty() {
        ScrapePort scrapePort = mock(ScrapePort.class);
        WorldScrapeSnapshot.Target target = new WorldScrapeSnapshot.Target(
                "Antica",
                "Open PvP",
                "EU",
                100,
                "regular",
                "regular"
        );
        when(scrapePort.fetchWorldPage(eq("Antica"), any(World.class))).thenReturn(new ScrapePort.WorldOnline(
                "Antica",
                0,
                null,
                null,
                null,
                null,
                null
        ));
        WorldScrapeClient client = new WorldScrapeClient(scrapePort);

        WorldScrapeSnapshot.Page page = client.fetchWorldPage(target);

        assertThat(page.players()).isEmpty();
        assertThat(page.playersOnline()).isZero();
    }
}
