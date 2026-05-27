package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.StatCategory;

public record HighscoreScope(
        Integer worldId,
        String worldName,
        StatCategory category,
        int vocationFilterId
) {
    public String label() {
        return worldName + "/" + category + "/vocation=" + vocationFilterId;
    }
}
