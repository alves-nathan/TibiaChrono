package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.GuildSnapshot;

public interface GuildSnapshotRepositoryPort {
    GuildSnapshot saveSnapshot(GuildSnapshot snapshot);
}
