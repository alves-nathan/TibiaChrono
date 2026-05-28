#!/usr/bin/env bash
set -euo pipefail

FILE="src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java"

if [[ ! -f "$FILE" ]]; then
  echo "File not found: $FILE" >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path
p = Path("src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java")
s = p.read_text()
old = '''            String title = extractTitleFromNameCell(row, characterLink, name);
            String rowRank = extractRankFromMemberRow(row, name, vocation, level);
            String rankName = firstNonBlankOrNull(rowRank, currentRank);

            members.add(new Member(name, blankToNull(rankName), blankToNull(title), blankToNull(vocation), level, joinedOn, online));
'''
new = '''            String title = extractTitleFromNameCell(row, characterLink, name);
            String rowRank = extractRankFromMemberRow(row, name, vocation, level);
            if (rowRank != null && !rowRank.isBlank()) {
                currentRank = rowRank;
            }
            String rankName = firstNonBlankOrNull(currentRank, rowRank);

            members.add(new Member(name, blankToNull(rankName), blankToNull(title), blankToNull(vocation), level, joinedOn, online));
'''
if old not in s:
    # Fallback for slightly different whitespace, but avoid destructive edits.
    old2 = '''            String rowRank = extractRankFromMemberRow(row, name, vocation, level);
            String rankName = firstNonBlankOrNull(rowRank, currentRank);
'''
    new2 = '''            String rowRank = extractRankFromMemberRow(row, name, vocation, level);
            if (rowRank != null && !rowRank.isBlank()) {
                currentRank = rowRank;
            }
            String rankName = firstNonBlankOrNull(currentRank, rowRank);
'''
    if old2 not in s:
        raise SystemExit("Expected rank extraction block not found. The file may already be fixed or has diverged.")
    s = s.replace(old2, new2, 1)
else:
    s = s.replace(old, new, 1)
p.write_text(s)
PY

echo "Applied guild rank rowspan propagation fix."
echo "Run: ./run-tests.sh"
