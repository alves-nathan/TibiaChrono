package com.nathan.tibiastats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "tibiastats.scrape.guilds")
public class GuildScrapeProperties {
    private boolean enabled = true;
    private long rateMs = 3_600_000L;
    private long initialDelayMs = 30_000L;
    private int worldLimit = 0;
    private int guildLimit = 50;
    private int pageDelayMs = 750;
    private boolean listEnabled = true;
    private boolean detailsEnabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getRateMs() { return Math.max(1_000L, rateMs); }
    public void setRateMs(long rateMs) { this.rateMs = rateMs; }

    public long getInitialDelayMs() { return Math.max(0L, initialDelayMs); }
    public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }

    public int getWorldLimit() { return Math.max(0, worldLimit); }
    public void setWorldLimit(int worldLimit) { this.worldLimit = worldLimit; }

    public int getGuildLimit() { return Math.max(1, guildLimit); }
    public void setGuildLimit(int guildLimit) { this.guildLimit = guildLimit; }

    public int getPageDelayMs() { return Math.max(0, pageDelayMs); }
    public void setPageDelayMs(int pageDelayMs) { this.pageDelayMs = pageDelayMs; }

    public boolean isListEnabled() { return listEnabled; }
    public void setListEnabled(boolean listEnabled) { this.listEnabled = listEnabled; }

    public boolean isDetailsEnabled() { return detailsEnabled; }
    public void setDetailsEnabled(boolean detailsEnabled) { this.detailsEnabled = detailsEnabled; }
}
