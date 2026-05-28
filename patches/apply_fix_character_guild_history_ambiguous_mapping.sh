#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(pwd)"
TARGET_FILE="$ROOT_DIR/src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java"
PATCH_NAME="fix-character-guild-history-ambiguous-mapping"
BACKUP_DIR="$ROOT_DIR/patches/.backups/$PATCH_NAME-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "$ROOT_DIR/pom.xml" || ! -d "$ROOT_DIR/src/main/java" ]]; then
  echo "ERROR: run this script from the TibiaChrono project root." >&2
  exit 1
fi

if [[ ! -f "$TARGET_FILE" ]]; then
  echo "ERROR: expected file not found: $TARGET_FILE" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
cp "$TARGET_FILE" "$BACKUP_DIR/GuildController.java"

python3 - <<'PY'
from pathlib import Path

path = Path("src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java")
text = path.read_text()
original = text

method = '''
    @GetMapping("/api/characters/{name}/guild-history")
    public List<GuildQueryService.GuildMemberView> getCharacterGuildHistory(@PathVariable String name) {
        try {
            return guilds.findCharacterGuildHistory(name);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }
'''

if method in text:
    text = text.replace(method, "\n", 1)
else:
    # Idempotent fallback: remove the exact handler block even if spacing changed slightly.
    marker = '    @GetMapping("/api/characters/{name}/guild-history")'
    idx = text.find(marker)
    if idx != -1:
        next_mapping = text.find('\n    @', idx + len(marker))
        if next_mapping == -1:
            raise SystemExit("Could not safely find the end of the duplicate guild-history handler.")
        text = text[:idx] + text[next_mapping + 1:]

# Normalize excessive blank lines created by removal.
while "\n\n\n" in text:
    text = text.replace("\n\n\n", "\n\n")

if text != original:
    path.write_text(text)
    print("Removed duplicate /api/characters/{name}/guild-history mapping from GuildController.")
else:
    print("No duplicate GuildController guild-history mapping found; file already appears fixed.")
PY

if grep -R "@GetMapping(\"/api/characters/{name}/guild-history\")" -n src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest/GuildController.java >/dev/null 2>&1; then
  echo "ERROR: duplicate GuildController mapping still exists after patch." >&2
  exit 1
fi

MAPPINGS="$(grep -R "guild-history" -n src/main/java/com/nathan/tibiastats/infrastructure/adapter/web/rest || true)"
if [[ -n "$MAPPINGS" ]]; then
  echo "Remaining guild-history REST references:"
  echo "$MAPPINGS"
else
  echo "No remaining guild-history REST references found."
fi

echo
echo "Patch applied successfully."
echo "Backup saved under: $BACKUP_DIR"
echo "Next step: run make test"
