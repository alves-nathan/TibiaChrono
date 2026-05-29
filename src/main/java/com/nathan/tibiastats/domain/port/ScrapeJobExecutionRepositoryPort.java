package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.ScrapeJobExecution;

import java.util.Optional;

public interface ScrapeJobExecutionRepositoryPort {
    <S extends ScrapeJobExecution> S save(S job);

    Optional<ScrapeJobExecution> findById(Long id);
}
