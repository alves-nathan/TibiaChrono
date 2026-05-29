package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.ScrapeJobExecution;
import com.nathan.tibiastats.domain.port.ScrapeJobExecutionRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapeJobExecutionRepository extends
        JpaRepository<ScrapeJobExecution, Long>,
        ScrapeJobExecutionRepositoryPort {
}
