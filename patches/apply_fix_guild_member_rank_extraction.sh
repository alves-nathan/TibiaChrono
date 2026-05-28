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

old = '''            String title = extractTitleFromNameCell(row, characterLink, name);

            members.add(new Member(name, blankToNull(currentRank), blankToNull(title), blankToNull(vocation), level, joinedOn, online));
'''
new = '''            String title = extractTitleFromNameCell(row, characterLink, name);
            String rowRank = extractRankFromMemberRow(row, name, vocation, level);
            String rankName = firstNonBlankOrNull(rowRank, currentRank);

            members.add(new Member(name, blankToNull(rankName), blankToNull(title), blankToNull(vocation), level, joinedOn, online));
'''
if old not in text:
    # Idempotent / already applied check
    if 'String rowRank = extractRankFromMemberRow(row, name, vocation, level);' not in text:
        raise SystemExit('Could not replace member add block safely. Please check JsoupGuildAdapter.java.')
else:
    text = text.replace(old, new, 1)

helper = r'''    private static String extractRankFromMemberRow(Element row, String name, String vocation, Integer level) {
        String rank = normalize(row.text()).replace("[sort]", " ");
        if (rank.isBlank()) return null;

        String linkText = row.select("a[href*=subtopic=characters], a[href*=characters]").stream()
                .map(Element::text)
                .map(JsoupGuildAdapter::normalize)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse("");

        rank = removeWholeValue(rank, name);
        rank = removeWholeValue(rank, linkText);
        if (vocation != null) rank = removeWholeValue(rank, vocation);
        if (level != null) rank = removeWholeValue(rank, String.valueOf(level));

        for (Element td : row.select("td")) {
            String value = normalize(td.text());
            if (parseDate(value) != null || isOnlineStatus(value) || LEVEL_PATTERN.matcher(value).matches()) {
                rank = removeWholeValue(rank, value);
            }
        }

        rank = rank.replaceAll("(?i)\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2}\\s+\\d{4}\\b", " ");
        rank = rank.replaceAll("(?i)\\b(online|offline)\\b", " ");
        rank = rank.replaceAll("(?i)\\b(name and title|vocation|level|joining date|status)\\b", " ");
        rank = normalize(rank);

        if (rank.isBlank()) return null;
        if (parseDate(rank) != null || isOnlineStatus(rank) || LEVEL_PATTERN.matcher(rank).matches()) return null;
        String lower = rank.toLowerCase(Locale.ROOT);
        if (lower.contains("name and title") || lower.contains("joining date") || lower.contains("vocation")) return null;
        if (lower.equals(name.toLowerCase(Locale.ROOT))) return null;
        return rank;
    }

'''

if 'private static String extractRankFromMemberRow(' not in text:
    marker = '    private static String extractTitleFromNameCell(Element row, Element characterLink, String name) {'
    if marker not in text:
        raise SystemExit('Could not locate extractTitleFromNameCell marker safely. Please check JsoupGuildAdapter.java.')
    text = text.replace(marker, helper + marker, 1)

path.write_text(text)
print('Updated JsoupGuildAdapter member rank extraction.')
PY

echo "Done. Run: ./run-tests.sh"
