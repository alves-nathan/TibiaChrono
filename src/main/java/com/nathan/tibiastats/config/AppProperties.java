package com.nathan.tibiastats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "tibiastats.scrape")
public class AppProperties {
    private Worlds worlds = new Worlds();
    private Highscores highscores = new Highscores();

    public static class Worlds {
        private long rateMs = 60000L;
        public long getRateMs(){return rateMs;}
        public void setRateMs(long v){this.rateMs=v;}
    }
    public static class Highscores {
        private String cron = "0 0 7 * * *";
        public String getCron(){return cron;}
        public void setCron(String c){this.cron=c;}
    }

    public Worlds getWorlds() {return worlds;}
    public Highscores getHighscores() {return highscores;}
}