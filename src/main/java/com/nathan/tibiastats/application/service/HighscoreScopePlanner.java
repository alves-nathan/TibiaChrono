package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.config.HighscoreScrapeProperties;
import com.nathan.tibiastats.domain.model.StatCategory;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import com.nathan.tibiastats.infrastructure.persistence.HighscoreScrapeStateRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class HighscoreScopePlanner {
    private final WorldRepositoryPort worldRepository;
    private final HighscoreScrapeStateRepository stateRepository;

    public HighscoreScopePlanner(
            WorldRepositoryPort worldRepository,
            HighscoreScrapeStateRepository stateRepository
    ) {
        this.worldRepository = worldRepository;
        this.stateRepository = stateRepository;
    }

    public HighscoreScopeSelection selectScopes(HighscoreScrapeProperties.Plan plan) {
        List<World> worlds = worldRepository.findAll().stream()
                .sorted(Comparator.comparing(World::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(plan.getWorldLimit() > 0 ? plan.getWorldLimit() : Long.MAX_VALUE)
                .toList();
        List<StatCategory> categories = plan.categoryList();
        List<Integer> vocationFilterIds = plan.vocationFilterIds();

        if (worlds.isEmpty()) {
            return HighscoreScopeSelection.empty(worlds, categories, vocationFilterIds);
        }

        stateRepository.registerScopes(worlds, categories, vocationFilterIds);
        List<HighscoreScope> scopes = stateRepository.findNextScopes(
                worlds,
                categories,
                vocationFilterIds,
                plan.getScopesPerRun()
        );
        return new HighscoreScopeSelection(worlds, categories, vocationFilterIds, scopes);
    }

    public record HighscoreScopeSelection(
            List<World> worlds,
            List<StatCategory> categories,
            List<Integer> vocationFilterIds,
            List<HighscoreScope> scopes
    ) {
        static HighscoreScopeSelection empty(
                List<World> worlds,
                List<StatCategory> categories,
                List<Integer> vocationFilterIds
        ) {
            return new HighscoreScopeSelection(worlds, categories, vocationFilterIds, List.of());
        }

        boolean hasWorlds() {
            return !worlds.isEmpty();
        }

        boolean hasScopes() {
            return !scopes.isEmpty();
        }

        int worldCount() {
            return worlds.size();
        }

        int categoryCount() {
            return categories.size();
        }

        int vocationCount() {
            return vocationFilterIds.size();
        }
    }
}
