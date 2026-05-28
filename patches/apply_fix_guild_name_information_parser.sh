#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

FILE="src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java"
if [[ ! -f "$FILE" ]]; then
  echo "ERROR: $FILE not found. Run this script from the TibiaChrono project root." >&2
  exit 1
fi

cp "$FILE" "$FILE.bak-guild-name-information-fix"

python3 - <<'PY'
from pathlib import Path
import re

path = Path("src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java")
text = path.read_text()
original = text

# Prefer the requested/canonical name passed to fetchGuildDetail. The Tibia detail
# page may contain generic headings such as "Guild Information", and parsing those
# as the guild name corrupts the persisted guild name.
text = re.sub(
    r'String\s+name\s*=\s*firstNonBlank\(\s*extractGuildNameFromDetailPage\(doc\),\s*normalize\(guildName\)\s*\);',
    'String requestedGuildName = normalize(guildName);\n            String extractedGuildName = sanitizeExtractedGuildName(extractGuildNameFromDetailPage(doc));\n            String name = firstNonBlank(\n                    requestedGuildName,\n                    extractedGuildName\n            );',
    text,
    count=1,
    flags=re.DOTALL,
)

# Some local formatter/patch versions have a multi-line block already; handle it explicitly.
old_block = '''String name = firstNonBlank(
                    extractGuildNameFromDetailPage(doc),
                    normalize(guildName)
            );'''
new_block = '''String requestedGuildName = normalize(guildName);
            String extractedGuildName = sanitizeExtractedGuildName(extractGuildNameFromDetailPage(doc));
            String name = firstNonBlank(
                    requestedGuildName,
                    extractedGuildName
            );'''
if old_block in text:
    text = text.replace(old_block, new_block, 1)

# Make the extractor itself defensive so it never returns generic page labels.
condition_old = 'if (!candidate.equalsIgnoreCase("Guilds") && candidate.length() <= 80) return candidate;'
condition_new = 'if (isValidExtractedGuildName(candidate) && candidate.length() <= 80) return candidate;'
if condition_old in text:
    text = text.replace(condition_old, condition_new, 1)

# Insert helper methods before extractGuildNameFromDetailPage if not already present.
if 'private static String sanitizeExtractedGuildName(String value)' not in text:
    marker = '    private static String extractGuildNameFromDetailPage(Document doc) {'
    helpers = '''    private static String sanitizeExtractedGuildName(String value) {
        String normalized = normalize(value);
        return isValidExtractedGuildName(normalized) ? normalized : null;
    }

    private static boolean isValidExtractedGuildName(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return false;
        String lower = normalized.toLowerCase(Locale.ROOT);
        return !lower.equals("guilds")
                && !lower.equals("information")
                && !lower.equals("guild information")
                && !lower.equals("guild members")
                && !lower.equals("invited characters")
                && !lower.equals("navigation");
    }

'''
    if marker not in text:
        raise SystemExit('Could not find extractGuildNameFromDetailPage marker to insert helpers')
    text = text.replace(marker, helpers + marker, 1)

if 'requestedGuildName' not in text:
    raise SystemExit('Patch failed: fetchGuildDetail name assignment was not updated')

if text == original:
    print('No code changes were necessary; JsoupGuildAdapter already looked patched.')
else:
    path.write_text(text)
    print('Patched JsoupGuildAdapter to preserve requested guild name and reject generic "Information" heading.')
PY

cat <<'MSG'

Done.

Recommended validation:
  ./run-tests.sh

Important: if your local dev database already got the wrong guild name "Information",
repair that dev data once before scraping Raw Raw again. See the SQL command in the ChatGPT message.
MSG
