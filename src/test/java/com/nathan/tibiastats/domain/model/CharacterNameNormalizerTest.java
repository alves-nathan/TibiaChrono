package com.nathan.tibiastats.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterNameNormalizerTest {
    @Test
    void normalizesTibiaUiNoiseAndWhitespace() {
        assertThat(CharacterNameNormalizer.normalize("  Elder\u00A0Druid   Hero (traded)  "))
                .isEqualTo("Elder Druid Hero");
        assertThat(CharacterNameNormalizer.normalize("Knight (Traded) (traded)"))
                .isEqualTo("Knight");
        assertThat(CharacterNameNormalizer.normalize("Mage     Name"))
                .isEqualTo("Mage Name");
    }

    @Test
    void handlesNullAndBlankNamesConsistently() {
        assertThat(CharacterNameNormalizer.normalize(null)).isNull();
        assertThat(CharacterNameNormalizer.isBlank(null)).isTrue();
        assertThat(CharacterNameNormalizer.isBlank("   (traded)   ")).isTrue();
        assertThat(CharacterNameNormalizer.normalizedKey(null)).isNull();
    }

    @Test
    void comparesNamesAfterNormalizationIgnoringCase() {
        assertThat(CharacterNameNormalizer.sameName("Raw  Raw (traded)", "raw raw")).isTrue();
        assertThat(CharacterNameNormalizer.sameName(null, null)).isTrue();
        assertThat(CharacterNameNormalizer.sameName(null, "Raw Raw")).isFalse();
        assertThat(CharacterNameNormalizer.sameName("Raw Raw", "Other Raw")).isFalse();
    }

    @Test
    void normalizesCollectionsAndCsvDroppingBlankEntries() {
        assertThat(CharacterNameNormalizer.normalizeMany(List.of(" Raw Raw ", " ", "Alt (traded)", "Mage\u00A0Name")))
                .containsExactly("Raw Raw", "Alt", "Mage Name");
        assertThat(CharacterNameNormalizer.normalizeMany(null)).isEmpty();

        assertThat(CharacterNameNormalizer.normalizeCsvToList(" Raw Raw, , Alt (traded),Mage\u00A0Name "))
                .containsExactly("Raw Raw", "Alt", "Mage Name");
        assertThat(CharacterNameNormalizer.normalizeCsv(null)).isEmpty();
        assertThat(CharacterNameNormalizer.normalizeCsv(" Raw Raw, Alt (traded) "))
                .isEqualTo("Raw Raw,Alt");
        assertThat(CharacterNameNormalizer.normalizedKey(" Raw RAW (traded) "))
                .isEqualTo("raw raw");
    }
}
