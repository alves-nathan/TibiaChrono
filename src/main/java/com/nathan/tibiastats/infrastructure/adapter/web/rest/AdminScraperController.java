package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.AdminScraperService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/scrapers")
public class AdminScraperController {
    private final AdminScraperService adminScrapers;

    public AdminScraperController(AdminScraperService adminScrapers) {
        this.adminScrapers = adminScrapers;
    }

    @GetMapping("/status")
    public AdminScraperService.ScraperStatusResponse status() {
        return adminScrapers.status();
    }

    @GetMapping("/highscores/backoff")
    public AdminScraperService.HighscoreBackoffStatus highscoreBackoff() {
        return adminScrapers.highscoreBackoffStatus();
    }

    @PostMapping("/highscores/backoff/reset")
    public AdminScraperService.HighscoreBackoffStatus resetHighscoreBackoff() {
        return adminScrapers.resetHighscoreBackoff();
    }

    @PostMapping("/worlds/run")
    public ResponseEntity<AdminScraperService.ManualRunResponse> runWorlds() {
        return accepted(() -> adminScrapers.triggerWorlds());
    }

    @PostMapping("/character-details/run")
    public ResponseEntity<AdminScraperService.ManualRunResponse> runCharacterDetails() {
        return accepted(() -> adminScrapers.triggerCharacterDetails());
    }

    @PostMapping("/guilds/run")
    public ResponseEntity<AdminScraperService.ManualRunResponse> runGuilds() {
        return accepted(() -> adminScrapers.triggerGuilds());
    }

    @PostMapping("/highscores/plans/{planName}/run")
    public ResponseEntity<AdminScraperService.ManualRunResponse> runHighscorePlan(@PathVariable String planName) {
        return accepted(() -> adminScrapers.triggerHighscorePlan(planName));
    }

    private ResponseEntity<AdminScraperService.ManualRunResponse> accepted(ManualRunTrigger trigger) {
        try {
            return ResponseEntity.accepted().body(trigger.start());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    @FunctionalInterface
    private interface ManualRunTrigger {
        AdminScraperService.ManualRunResponse start();
    }
}
