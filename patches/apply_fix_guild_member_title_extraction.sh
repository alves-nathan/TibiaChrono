#!/usr/bin/env bash
set -euo pipefail

FILE="src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java"

if [[ ! -f "$FILE" ]]; then
  echo "ERROR: $FILE not found. Run this from the TibiaChrono project root." >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path
import re

path = Path('src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java')
text = path.read_text()

new_method = '''    private static String extractTitleFromNameCell(Element row, Element characterLink, String name) {
        Element cell = characterLink.parents().stream()
                .filter(e -> "td".equalsIgnoreCase(e.tagName()))
                .filter(e -> normalize(e.text()).toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseGet(() -> row.select("td").stream()
                        .filter(e -> normalize(e.text()).toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                        .findFirst()
                        .orElse(row.selectFirst("td")));
        if (cell == null) return null;

        String title = normalize(cell.text());
        String linkText = normalize(characterLink.text());
        if (!linkText.isBlank()) title = removeWholeValue(title, linkText);
        title = removeWholeValue(title, name);

        for (String vocation : VOCATION_WORDS) {
            title = removeWholeValue(title, vocation);
        }

        for (String part : title.split("\\\\s+")) {
            String normalized = normalize(part);
            if (LEVEL_PATTERN.matcher(normalized).matches()) {
                title = removeWholeValue(title, normalized);
            }
        }

        for (Element td : row.select("td")) {
            String value = normalize(td.text());
            LocalDate date = parseDate(value);
            if (date != null) title = removeWholeValue(title, value);
        }

        title = title.replaceAll("(?i)\\\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\\\s+\\\\d{1,2}\\\\s+\\\\d{4}\\\\b", " ");
        title = title.replaceAll("(?i)\\\\b(online|offline)\\\\b", " ");
        title = normalize(title);
        if (title.isBlank()) return null;
        if (parseDate(title) != null || isOnlineStatus(title)) return null;
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("name and title") || lower.contains("vocation") || lower.contains("joining date")) return null;
        if (lower.equals(name.toLowerCase(Locale.ROOT))) return null;
        return title;
    }

'''

pattern = re.compile(r'    private static String extractTitleFromNameCell\(Element row, Element characterLink, String name\) \{.*?^    \}\n\n(?=    private static String removeLeadingValue)', re.S | re.M)
text2, count = pattern.subn(new_method, text)
if count != 1:
    raise SystemExit('Could not replace extractTitleFromNameCell safely. Please check JsoupGuildAdapter.java.')

helper = '''
    private static String removeWholeValue(String text, String value) {
        if (text == null || value == null || value.isBlank()) return text;
        String normalizedText = normalize(text);
        String normalizedValue = normalize(value);
        if (normalizedText.equalsIgnoreCase(normalizedValue)) return "";
        return normalize(normalizedText.replaceAll("(?i)(^|\\\\s)" + Pattern.quote(normalizedValue) + "(?=\\\\s|$)", " "));
    }
'''

if 'private static String removeWholeValue(' not in text2:
    marker = re.compile(r'(    private static String removeLeadingValue\(String text, String value\) \{.*?^    \}\n)', re.S | re.M)
    text2, count2 = marker.subn(r'\1' + helper, text2)
    if count2 != 1:
        raise SystemExit('Could not add removeWholeValue helper safely. Please check JsoupGuildAdapter.java.')

path.write_text(text2)
print('Updated JsoupGuildAdapter title extraction.')
PY

echo "Done. Run: ./run-tests.sh"
