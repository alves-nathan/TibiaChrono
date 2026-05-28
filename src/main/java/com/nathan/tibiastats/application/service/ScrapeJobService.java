package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.ScrapeJobExecution;
import com.nathan.tibiastats.infrastructure.persistence.ScrapeJobExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class ScrapeJobService {
    public static final String WORLD_SCRAPER = "WORLD_SCRAPER";
    public static final String CHARACTER_DETAILS_SCRAPER = "CHARACTER_DETAILS_SCRAPER";
    public static final String HIGHSCORE_SCRAPER = "HIGHSCORE_SCRAPER";
    public static final String GUILD_SCRAPER = "GUILD_SCRAPER";

    private final ScrapeJobExecutionRepository repository;

    public ScrapeJobService(ScrapeJobExecutionRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(String jobName) {
        ScrapeJobExecution job = new ScrapeJobExecution();
        job.setJobName(jobName);
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());
        job.setItemsProcessed(0);
        job.setItemsCreated(0);
        job.setItemsUpdated(0);
        job.setItemsFailed(0);
        return repository.save(job).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishSuccess(Long jobId, ScrapeJobResult result) {
        ScrapeJobExecution job = repository.findById(jobId).orElseThrow();
        Instant finishedAt = Instant.now();
        job.setStatus("SUCCESS");
        job.setFinishedAt(finishedAt);
        job.setDurationMs(Duration.between(job.getStartedAt(), finishedAt).toMillis());
        job.setItemsProcessed(result.itemsProcessed());
        job.setItemsCreated(result.itemsCreated());
        job.setItemsUpdated(result.itemsUpdated());
        job.setItemsFailed(result.itemsFailed());
        job.setErrorMessage(null);
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishFailure(Long jobId, ScrapeJobResult partialResult, Throwable error) {
        ScrapeJobExecution job = repository.findById(jobId).orElseThrow();
        Instant finishedAt = Instant.now();
        job.setStatus("FAILED");
        job.setFinishedAt(finishedAt);
        job.setDurationMs(Duration.between(job.getStartedAt(), finishedAt).toMillis());
        job.setItemsProcessed(partialResult == null ? 0 : partialResult.itemsProcessed());
        job.setItemsCreated(partialResult == null ? 0 : partialResult.itemsCreated());
        job.setItemsUpdated(partialResult == null ? 0 : partialResult.itemsUpdated());
        job.setItemsFailed(partialResult == null ? 1 : Math.max(1, partialResult.itemsFailed()));
        job.setErrorMessage(truncate(error == null ? "Unknown error" : error.getMessage(), 4000));
        repository.save(job);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
