package com.nathan.tibiastats.domain.port;

import java.time.Instant;
import java.util.List;

public interface AnalyticsQueryPort {
    int getCurrentOnlineTotal();
    List<RecordPoint> getWorldOnlineHistory(String world, Instant from, Instant to);
    record RecordPoint(Instant timestamp, int playersOnline) {}
}