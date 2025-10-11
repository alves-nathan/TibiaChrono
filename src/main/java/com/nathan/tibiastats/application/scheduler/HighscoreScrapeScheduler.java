package com.nathan.tibiastats.application.scheduler;

import com.nathan.tibiastats.config.AppProperties;
import com.nathan.tibiastats.application.service.HighscoreService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HighscoreScrapeScheduler {
    private final HighscoreService service; private final AppProperties props;
    public HighscoreScrapeScheduler(HighscoreService s, AppProperties p){this.service=s; this.props=p;}

    @Scheduled(cron = "${tibiastats.scrape.highscores.cron:0 0 3 * * *}")
    public void run(){ service.updateAllHighscores(); }
}