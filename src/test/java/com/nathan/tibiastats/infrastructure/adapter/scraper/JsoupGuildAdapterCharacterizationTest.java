package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.GuildScrapePort.GuildDetail;
import com.nathan.tibiastats.domain.port.GuildScrapePort.Member;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupGuildAdapterCharacterizationTest {

    @Test
    void parsesGuildDetailMembersAndInvitesFromFixtureWithoutNetworkAccess() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/fixtures/tibia/guild-detail.html"));

        GuildDetail detail = new JsoupGuildAdapter().parseGuildDetailHtml(html, "Raw Raw");

        assertThat(detail.name()).isEqualTo("Raw Raw");
        assertThat(detail.worldName()).isEqualTo("Antica");
        assertThat(detail.description()).isEqualTo("Neutral guild");
        assertThat(detail.homepage()).isEqualTo("https://example.test");
        assertThat(detail.foundedAt()).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(detail.memberCount()).isEqualTo(2);
        assertThat(detail.onlineCount()).isEqualTo(1);
        assertThat(detail.rawHash()).isNotBlank();

        assertThat(detail.members()).extracting(Member::name)
                .contains("Guild Leader", "Guild Member");
        assertThat(detail.members())
                .anySatisfy(member -> {
                    assertThat(member.name()).isEqualTo("Guild Leader");
                    assertThat(member.title()).isEqualTo("The Boss");
                    assertThat(member.vocation()).isEqualTo("Elite Knight");
                    assertThat(member.level()).isEqualTo(500);
                    assertThat(member.joinedOn()).isEqualTo(LocalDate.of(2026, 5, 1));
                    assertThat(member.online()).isTrue();
                });
        assertThat(detail.invites()).extracting(invite -> invite.characterName())
                .contains("Invited Char");
    }
}
