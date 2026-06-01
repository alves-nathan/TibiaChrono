package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class CharacterProfileFieldsParser {

    Map<String, String> collectCharacterFields(Document doc) {
        Map<String, String> fields = new LinkedHashMap<>();

        Elements labelCells = doc.select("td.LabelV");
        for (Element labelCell : labelCells) {
            Element valueCell = labelCell.nextElementSibling();
            addField(fields, labelCell, valueCell);
        }

        if (fields.isEmpty()) {
            Elements rows = doc.select("table.TableContent tr");
            for (Element row : rows) {
                Elements cols = row.select("> td");
                if (cols.size() >= 2) {
                    addField(fields, cols.get(0), cols.get(1));
                }
            }
        }

        return fields;
    }

    boolean isCharacterNotFound(Document doc) {
        String text = doc.text().toLowerCase(Locale.ROOT);
        return text.contains("could not find character")
                || text.contains("character does not exist")
                || text.contains("no character with this name");
    }

    private void addField(Map<String, String> fields, Element labelCell, Element valueCell) {
        if (labelCell == null || valueCell == null) {
            return;
        }

        String label = normalizeLabel(labelCell.text());
        String value = normalizeValue(valueCell.text());

        if (!label.isBlank() && !value.isBlank()) {
            fields.putIfAbsent(label, value);
        }
    }

    private String normalizeLabel(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(':', ' ')
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }
}
