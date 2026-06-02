package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.ScrapeJobExecution;
import com.nathan.tibiastats.domain.port.ScrapeJobExecutionRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScrapeJobServiceTest {
    @Test
    void startCreatesRunningJobWithZeroedCounters() {
        ScrapeJobExecutionRepositoryPort repository = mock(ScrapeJobExecutionRepositoryPort.class);
        AtomicReference<ScrapeJobExecution> savedJob = new AtomicReference<>();
        when(repository.save(any(ScrapeJobExecution.class))).thenAnswer(invocation -> {
            ScrapeJobExecution job = invocation.getArgument(0);
            job.setId(42L);
            savedJob.set(job);
            return job;
        });
        ScrapeJobService service = new ScrapeJobService(repository);

        Long jobId = service.start(ScrapeJobService.WORLD_SCRAPER);

        assertThat(jobId).isEqualTo(42L);
        assertThat(savedJob.get().getJobName()).isEqualTo(ScrapeJobService.WORLD_SCRAPER);
        assertThat(savedJob.get().getStatus()).isEqualTo("RUNNING");
        assertThat(savedJob.get().getStartedAt()).isNotNull();
        assertThat(savedJob.get().getItemsProcessed()).isZero();
        assertThat(savedJob.get().getItemsCreated()).isZero();
        assertThat(savedJob.get().getItemsUpdated()).isZero();
        assertThat(savedJob.get().getItemsFailed()).isZero();
    }

    @Test
    void finishSuccessStoresCountersDurationAndClearsError() {
        ScrapeJobExecutionRepositoryPort repository = mock(ScrapeJobExecutionRepositoryPort.class);
        ScrapeJobExecution job = runningJob(7L);
        job.setErrorMessage("previous error");
        when(repository.findById(7L)).thenReturn(Optional.of(job));
        when(repository.save(any(ScrapeJobExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ScrapeJobService service = new ScrapeJobService(repository);

        service.finishSuccess(7L, ScrapeJobResult.of(10, 2, 7, 1));

        assertThat(job.getStatus()).isEqualTo("SUCCESS");
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(job.getItemsProcessed()).isEqualTo(10);
        assertThat(job.getItemsCreated()).isEqualTo(2);
        assertThat(job.getItemsUpdated()).isEqualTo(7);
        assertThat(job.getItemsFailed()).isEqualTo(1);
        assertThat(job.getErrorMessage()).isNull();
    }

    @Test
    void finishFailureStoresPartialCountersAndAtLeastOneFailure() {
        ScrapeJobExecutionRepositoryPort repository = mock(ScrapeJobExecutionRepositoryPort.class);
        ScrapeJobExecution job = runningJob(9L);
        when(repository.findById(9L)).thenReturn(Optional.of(job));
        when(repository.save(any(ScrapeJobExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ScrapeJobService service = new ScrapeJobService(repository);

        service.finishFailure(9L, ScrapeJobResult.of(5, 1, 3, 0), new IllegalStateException("boom"));

        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(job.getItemsProcessed()).isEqualTo(5);
        assertThat(job.getItemsCreated()).isEqualTo(1);
        assertThat(job.getItemsUpdated()).isEqualTo(3);
        assertThat(job.getItemsFailed()).isEqualTo(1);
        assertThat(job.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void finishFailureHandlesNullResultAndTruncatesLongMessages() {
        ScrapeJobExecutionRepositoryPort repository = mock(ScrapeJobExecutionRepositoryPort.class);
        ScrapeJobExecution job = runningJob(11L);
        when(repository.findById(11L)).thenReturn(Optional.of(job));
        when(repository.save(any(ScrapeJobExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ScrapeJobService service = new ScrapeJobService(repository);
        String longMessage = "x".repeat(4_500);

        service.finishFailure(11L, null, new RuntimeException(longMessage));

        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getItemsProcessed()).isZero();
        assertThat(job.getItemsCreated()).isZero();
        assertThat(job.getItemsUpdated()).isZero();
        assertThat(job.getItemsFailed()).isEqualTo(1);
        assertThat(job.getErrorMessage()).hasSize(4_000).containsOnlyOnce("x".repeat(4_000));
    }

    @Test
    void finishFailureUsesUnknownErrorWhenThrowableIsNull() {
        ScrapeJobExecutionRepositoryPort repository = mock(ScrapeJobExecutionRepositoryPort.class);
        ScrapeJobExecution job = runningJob(13L);
        when(repository.findById(13L)).thenReturn(Optional.of(job));
        when(repository.save(any(ScrapeJobExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ScrapeJobService service = new ScrapeJobService(repository);

        service.finishFailure(13L, ScrapeJobResult.empty(), null);

        assertThat(job.getErrorMessage()).isEqualTo("Unknown error");
        assertThat(job.getItemsFailed()).isEqualTo(1);
    }

    private ScrapeJobExecution runningJob(Long id) {
        ScrapeJobExecution job = new ScrapeJobExecution();
        job.setId(id);
        job.setJobName(ScrapeJobService.WORLD_SCRAPER);
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now().minusMillis(25));
        return job;
    }
}
