package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.HighscoreHttpBackoffState;
import com.nathan.tibiastats.domain.model.HighscoreScope;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;

import java.util.List;

public interface HighscoreScrapeStateRepositoryPort {
    void registerScopes(List<World> worlds, List<StatCategory> categories, List<Integer> vocationFilterIds);

    List<HighscoreScope> findNextScopes(
            List<World> worlds,
            List<StatCategory> categories,
            List<Integer> vocationFilterIds,
            int limit
    );

    void markStarted(HighscoreScope scope);

    void markFinished(HighscoreScope scope, String status, int pageCount, int rowCount, long durationMs, String error);

    HighscoreHttpBackoffState getHttpBackoffState();

    HighscoreHttpBackoffState activateHttpBackoff(long initialCooldownMs, long maxCooldownMs, double multiplier, String reason);

    void resetHttpBackoffAfterSuccess();
}
