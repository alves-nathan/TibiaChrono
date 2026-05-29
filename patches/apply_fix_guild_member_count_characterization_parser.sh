#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="fix-guild-member-count-characterization-parser"
BACKUP_DIR="patches/.backups/${PATCH_NAME}-$(date +%Y%m%d%H%M%S)"
TARGET="src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java"

if [[ ! -f "$TARGET" ]]; then
  echo "ERROR: $TARGET not found. Run this patch from the project root." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR/$(dirname "$TARGET")"
cp "$TARGET" "$BACKUP_DIR/$TARGET"

python3 <<'PY'
from pathlib import Path

path = Path("src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java")
text = path.read_text()
original = text

text = text.replace(
    'Integer memberCount = parseNumberBefore(pageText, "members");\n'
    '        Integer onlineCount = parseNumberBefore(pageText, "online");',
    'Integer memberCount = parseMemberCount(pageText);\n'
    '        Integer onlineCount = parseOnlineCount(pageText);'
)

if 'parseMemberCount(String text)' not in text:
    marker = '''    private static Integer parseNumberBefore(String text, String word) {
        Matcher matcher = Pattern.compile("(\\\\d+)\\\\s+" + Pattern.quote(word), Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }
'''
    helper = '''    private static Integer parseMemberCount(String text) {
        Integer labeledCount = parseNumberAfterLabel(text, "Members:");
        if (labeledCount != null) return labeledCount;
        return parseNumberBefore(text, "members");
    }

    private static Integer parseOnlineCount(String text) {
        Integer labeledCount = parseNumberAfterLabel(text, "Online:");
        if (labeledCount != null) return labeledCount;
        return parseNumberBefore(text, "online");
    }

    private static Integer parseNumberAfterLabel(String text, String label) {
        if (text == null || label == null) return null;
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "\\\\s*(\\\\d+)\\\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

'''
    if marker not in text:
        raise SystemExit("ERROR: could not locate parseNumberBefore helper insertion point in JsoupGuildAdapter.java")
    text = text.replace(marker, helper + marker)

if text == original:
    print("No changes needed; guild member count parser already appears to be fixed.")
else:
    path.write_text(text)
PY

chmod 0644 "$TARGET"

echo "Done. Guild member count parsing fixed to prefer the explicit Members: label."
echo "Backup directory: $BACKUP_DIR"
echo "Next step: make test"
