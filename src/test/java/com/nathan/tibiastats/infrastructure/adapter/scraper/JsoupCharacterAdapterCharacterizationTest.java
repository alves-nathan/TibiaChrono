package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterDetailPort.CharacterDetails;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupCharacterAdapterCharacterizationTest {

    @Test
    void parsesCharacterProfileFieldsFromFixtureWithoutNetworkAccess() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/fixtures/tibia/character-detail.html"));

        Optional<CharacterDetails> result = new JsoupCharacterAdapter()
                .parseCharacterDetailsHtml(html, "Sample Char");

        assertThat(result).isPresent();
        CharacterDetails details = result.orElseThrow();
        assertThat(details.currentName()).isEqualTo("Sample Char");
        assertThat(details.formerNames()).containsExactly("Old Sample", "Older Sample");
        assertThat(details.sex()).isEqualTo(CharacterEntity.Sex.male);
        assertThat(details.vocation()).isEqualTo("Elite Knight");
        assertThat(details.level()).isEqualTo(123);
        assertThat(details.achievementPoints()).isEqualTo(456);
        assertThat(details.residence()).isEqualTo("Thais");
        assertThat(details.lastLogin()).isEqualTo(OffsetDateTime.of(2026, 5, 27, 21, 15, 33, 0, ZoneOffset.ofHours(2)));
        assertThat(details.accountStatus()).isEqualTo("Premium Account");
        assertThat(details.creationDate()).isEqualTo(Instant.parse("2020-05-01T09:00:00Z"));
        assertThat(details.world()).isEqualTo("Antica");
    }
}
