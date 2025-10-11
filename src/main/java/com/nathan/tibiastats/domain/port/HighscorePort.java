package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.StatCategory;
import java.util.List;

public interface HighscorePort {
    record HighscoreRow(int rank, String name, long value) {}
    List<HighscoreRow> fetchHighscores(String world, StatCategory category, int vocationId, int page);
}