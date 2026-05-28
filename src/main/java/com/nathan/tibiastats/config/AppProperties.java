package com.nathan.tibiastats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "tibiastats.scrape")
public class AppProperties {
    private Worlds worlds = new Worlds();
    private Highscores highscores = new Highscores();
    private CharacterDetails characterDetails = new CharacterDetails();

    public static class Worlds {
        private boolean enabled = true;
        private long rateMs = 60000L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getRateMs(){return rateMs;}
        public void setRateMs(long v){this.rateMs=v;}
    }

    public static class Highscores {
        private boolean enabled = true;
        private String cron = "0 0 7 * * *";
        private String categories = "EXPERIENCE";
        private String vocations = "0";
        private int maxPages = 1;
        private long pageDelayMs = 1000L;
        private int worldLimit = 0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getCron(){return cron;}
        public void setCron(String c){this.cron=c;}

        public String getCategories() { return categories; }
        public void setCategories(String categories) { this.categories = categories; }

        public String getVocations() { return vocations; }
        public void setVocations(String vocations) { this.vocations = vocations; }

        public int getMaxPages() { return maxPages; }
        public void setMaxPages(int maxPages) { this.maxPages = maxPages; }

        public long getPageDelayMs() { return pageDelayMs; }
        public void setPageDelayMs(long pageDelayMs) { this.pageDelayMs = pageDelayMs; }

        public int getWorldLimit() { return worldLimit; }
        public void setWorldLimit(int worldLimit) { this.worldLimit = worldLimit; }
    }

    public static class CharacterDetails {
        private boolean enabled = true;
        private long rateMs = 300000L;
        private long initialDelayMs = 15000L;
        private int batchSize = 25;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getRateMs() { return rateMs; }
        public void setRateMs(long rateMs) { this.rateMs = rateMs; }

        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public Worlds getWorlds() {return worlds;}
    public void setWorlds(Worlds worlds) { this.worlds = worlds; }

    public Highscores getHighscores() {return highscores;}
    public void setHighscores(Highscores highscores) { this.highscores = highscores; }

    public CharacterDetails getCharacterDetails() { return characterDetails; }
    public void setCharacterDetails(CharacterDetails characterDetails) { this.characterDetails = characterDetails; }
}
