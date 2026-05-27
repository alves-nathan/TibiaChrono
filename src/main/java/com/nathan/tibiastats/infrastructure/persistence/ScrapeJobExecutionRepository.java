package com.nathan.tibiastats.infrastructure.persistence;

import com.nathan.tibiastats.domain.model.ScrapeJobExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapeJobExecutionRepository extends JpaRepository<ScrapeJobExecution, Long> {
}
