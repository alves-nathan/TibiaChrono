package com.nathan.tibiastats.domain.model;

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
