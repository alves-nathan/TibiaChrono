package com.nathan.tibiastats.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScrapeJobResultTest {
    @Test
    void emptyResultHasZeroCounters() {
        assertThat(ScrapeJobResult.empty())
                .isEqualTo(new ScrapeJobResult(0, 0, 0, 0));
    }

    @Test
    void factoryClampsNegativeCountersToZero() {
        assertThat(ScrapeJobResult.of(-1, 2, -3, 4))
                .isEqualTo(new ScrapeJobResult(0, 2, 0, 4));
    }
}
