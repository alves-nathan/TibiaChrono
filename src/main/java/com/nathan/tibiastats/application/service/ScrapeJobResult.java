package com.nathan.tibiastats.application.service;

public record ScrapeJobResult(
        int itemsProcessed,
        int itemsCreated,
        int itemsUpdated,
        int itemsFailed
) {
    public static ScrapeJobResult empty() {
        return new ScrapeJobResult(0, 0, 0, 0);
    }

    public static ScrapeJobResult of(int itemsProcessed, int itemsCreated, int itemsUpdated, int itemsFailed) {
        return new ScrapeJobResult(
                Math.max(0, itemsProcessed),
                Math.max(0, itemsCreated),
                Math.max(0, itemsUpdated),
                Math.max(0, itemsFailed)
        );
    }
}
