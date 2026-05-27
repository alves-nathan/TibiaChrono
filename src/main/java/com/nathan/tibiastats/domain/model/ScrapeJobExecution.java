package com.nathan.tibiastats.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scrape_jobs")
public class ScrapeJobExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "items_processed")
    private Integer itemsProcessed;

    @Column(name = "items_created")
    private Integer itemsCreated;

    @Column(name = "items_updated")
    private Integer itemsUpdated;

    @Column(name = "items_failed")
    private Integer itemsFailed;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Integer getItemsProcessed() { return itemsProcessed; }
    public void setItemsProcessed(Integer itemsProcessed) { this.itemsProcessed = itemsProcessed; }

    public Integer getItemsCreated() { return itemsCreated; }
    public void setItemsCreated(Integer itemsCreated) { this.itemsCreated = itemsCreated; }

    public Integer getItemsUpdated() { return itemsUpdated; }
    public void setItemsUpdated(Integer itemsUpdated) { this.itemsUpdated = itemsUpdated; }

    public Integer getItemsFailed() { return itemsFailed; }
    public void setItemsFailed(Integer itemsFailed) { this.itemsFailed = itemsFailed; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
