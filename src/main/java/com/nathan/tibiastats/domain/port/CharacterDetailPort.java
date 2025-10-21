package com.nathan.tibiastats.domain.port;

import java.util.List;

public interface CharacterDetailPort {
    record NameDetails(String currentName, List<String> formerNames) {}
    NameDetails fetchNameDetails(String worldName, String characterName);
}
