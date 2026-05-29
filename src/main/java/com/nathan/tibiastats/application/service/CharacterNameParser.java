package com.nathan.tibiastats.application.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class CharacterNameParser {
    public List<String> parseFormerNames(String formerNames, String currentName) {
        if (isBlank(formerNames)) {
            return List.of();
        }

        String normalizedCurrentName = normalizeName(currentName);
        List<String> parsed = new ArrayList<>();

        for (String part : formerNames.split(",")) {
            String normalized = normalizeName(part);
            if (!isBlank(normalized) && !sameName(normalized, normalizedCurrentName)) {
                parsed.add(normalized);
            }
        }

        return normalizeFormerNames(parsed, normalizedCurrentName);
    }

    public List<String> normalizeFormerNames(List<String> formerNames, String currentName) {
        if (formerNames == null || formerNames.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String formerName : formerNames) {
            String value = normalizeName(formerName);
            if (!isBlank(value) && !sameName(value, currentName)) {
                normalized.add(value);
            }
        }

        return List.copyOf(normalized);
    }

    public String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\s+", " ");
    }

    public boolean sameName(String a, String b) {
        return Objects.equals(normalizeName(a).toLowerCase(Locale.ROOT), normalizeName(b).toLowerCase(Locale.ROOT));
    }

    public boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
