package com.nathan.tibiastats.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesCoverageTest {
    @Test
    void rootPropertiesExposeAndReplaceNestedConfigurationObjects() {
        AppProperties properties = new AppProperties();
        AppProperties.Worlds worlds = new AppProperties.Worlds();
        AppProperties.Highscores highscores = new AppProperties.Highscores();
        AppProperties.CharacterDetails characterDetails = new AppProperties.CharacterDetails();

        worlds.setEnabled(false);
        worlds.setRateMs(123L);
        highscores.setEnabled(false);
        highscores.setCron("0 15 8 * * *");
        characterDetails.setEnabled(false);
        characterDetails.setRateMs(456L);
        characterDetails.setInitialDelayMs(789L);
        characterDetails.setBatchSize(7);

        properties.setWorlds(worlds);
        properties.setHighscores(highscores);
        properties.setCharacterDetails(characterDetails);

        assertThat(properties.getWorlds().isEnabled()).isFalse();
        assertThat(properties.getWorlds().getRateMs()).isEqualTo(123L);
        assertThat(properties.getHighscores().isEnabled()).isFalse();
        assertThat(properties.getHighscores().getCron()).isEqualTo("0 15 8 * * *");
        assertThat(properties.getCharacterDetails().isEnabled()).isFalse();
        assertThat(properties.getCharacterDetails().getRateMs()).isEqualTo(456L);
        assertThat(properties.getCharacterDetails().getInitialDelayMs()).isEqualTo(789L);
        assertThat(properties.getCharacterDetails().getBatchSize()).isEqualTo(7);
    }

    @Test
    void legacyHighscorePropertiesRoundTripEveryMutableValue() {
        AppProperties.Highscores highscores = new AppProperties.Highscores();

        assertThat(highscores.isEnabled()).isTrue();
        assertThat(highscores.getCron()).isEqualTo("0 0 7 * * *");
        assertThat(highscores.getCategories()).isEqualTo("EXPERIENCE");
        assertThat(highscores.getVocations()).isEqualTo("0");
        assertThat(highscores.getMaxPages()).isOne();
        assertThat(highscores.getPageDelayMs()).isEqualTo(1000L);
        assertThat(highscores.getWorldLimit()).isZero();

        highscores.setEnabled(false);
        highscores.setCron("0 0 12 * * *");
        highscores.setCategories("EXPERIENCE,MAGIC_LEVEL");
        highscores.setVocations("0,1,2");
        highscores.setMaxPages(42);
        highscores.setPageDelayMs(250L);
        highscores.setWorldLimit(3);

        assertThat(highscores.isEnabled()).isFalse();
        assertThat(highscores.getCron()).isEqualTo("0 0 12 * * *");
        assertThat(highscores.getCategories()).isEqualTo("EXPERIENCE,MAGIC_LEVEL");
        assertThat(highscores.getVocations()).isEqualTo("0,1,2");
        assertThat(highscores.getMaxPages()).isEqualTo(42);
        assertThat(highscores.getPageDelayMs()).isEqualTo(250L);
        assertThat(highscores.getWorldLimit()).isEqualTo(3);
    }
}
