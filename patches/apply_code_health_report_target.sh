#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="code-health-report-target"
BACKUP_DIR="patches/.backups/${PATCH_NAME}-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
for file in Makefile scripts/code-health-report.sh; do
  if [[ -f "$file" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
done

mkdir -p scripts
cat > scripts/code-health-report.sh <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="${1:-.}"
LIMIT="${LIMIT:-25}"
WARN_LOC="${WARN_LOC:-400}"
FAIL_LOC="${FAIL_LOC:-0}"

if [[ ! -d "$ROOT/src/main/java" ]]; then
  echo "ERROR: $ROOT does not look like a TibiaChrono project root." >&2
  exit 1
fi

cd "$ROOT"

echo "# TibiaChrono code health report"
echo
echo "Generated at: $(date -Iseconds)"
echo
echo "## Java source totals"

total_files=$(find src/main/java -name '*.java' -type f | wc -l | tr -d ' ')
total_loc=$(find src/main/java -name '*.java' -type f -print0 | xargs -0 awk 'END { print NR }')
printf -- '- Production Java files: %s\n' "$total_files"
printf -- '- Production Java LOC: %s\n' "$total_loc"
echo

echo "## Largest production Java files"
find src/main/java -name '*.java' -type f -print0 \
  | xargs -0 wc -l \
  | sort -nr \
  | awk -v limit="$LIMIT" '$2 != "total" && ++shown <= limit { printf "- %5d %s\n", $1, $2 }'
echo

echo "## Files above warning threshold (${WARN_LOC} LOC)"
above_warn=$(find src/main/java -name '*.java' -type f -print0 \
  | xargs -0 wc -l \
  | awk -v threshold="$WARN_LOC" '$1 >= threshold && $2 != "total" { printf "- %5d %s\n", $1, $2 }')
if [[ -n "$above_warn" ]]; then
  printf '%s\n' "$above_warn"
else
  echo "- none"
fi
echo

echo "## Package boundary hotspots"
printf -- '- application.service Java files: '
find src/main/java/com/nathan/tibiastats/application/service -name '*.java' -type f 2>/dev/null | wc -l | tr -d ' '
printf -- '- application.query Java files: '
find src/main/java/com/nathan/tibiastats/application/query -name '*.java' -type f 2>/dev/null | wc -l | tr -d ' '
printf -- '- scraper adapter Java files: '
find src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper -name '*.java' -type f 2>/dev/null | wc -l | tr -d ' '
echo

echo "## Direct JDBC usage outside application.query/infrastructure"
jdbc_usage=$(grep -R "org\.springframework\.jdbc" -n src/main/java 2>/dev/null \
  | grep -v '/application/query/' \
  | grep -v '/infrastructure/' || true)
if [[ -n "$jdbc_usage" ]]; then
  printf '%s\n' "$jdbc_usage"
else
  echo "- none"
fi
echo

echo "## Local artifact hygiene"
artifacts=$(find . \
  -path './.git' -prune -o \
  -path './.idea' -print -prune -o \
  -path './.test-maven' -print -prune -o \
  -path './target' -print -prune -o \
  -path './patches/.backups' -print -prune -o \
  \( -name '*.bak' -o -name '*.bak-*' -o -name '*.bak.*' -o -name '*Zone.Identifier*' \) -print | sort)
if [[ -n "$artifacts" ]]; then
  printf '%s\n' "$artifacts"
else
  echo "- none"
fi

if [[ "$FAIL_LOC" =~ ^[0-9]+$ && "$FAIL_LOC" -gt 0 ]]; then
  offenders=$(find src/main/java -name '*.java' -type f -print0 \
    | xargs -0 wc -l \
    | awk -v threshold="$FAIL_LOC" '$1 >= threshold && $2 != "total" { print $2 }')
  if [[ -n "$offenders" ]]; then
    echo
    echo "ERROR: files above FAIL_LOC=${FAIL_LOC}:" >&2
    printf '%s\n' "$offenders" >&2
    exit 1
  fi
fi
EOF
chmod 0755 scripts/code-health-report.sh

python3 - <<'PY'
from pathlib import Path

makefile = Path('Makefile')
text = makefile.read_text()
original = text

help_line = '\t@echo "  make code-health   - print class-size and architecture hotspot report"'
if 'make code-health' not in text:
    marker = '\t@echo "  make export-clean   - create a clean ZIP export without local artifacts"'
    if marker in text:
        text = text.replace(marker, marker + '\n' + help_line)
    else:
        text = text.replace('help:\n', 'help:\n' + help_line + '\n')

if '.PHONY: code-health' not in text:
    insert_after = '.PHONY: export-clean\nexport-clean:\n\t./scripts/export-clean.sh\n'
    addition = '''
.PHONY: code-health
code-health:
	./scripts/code-health-report.sh
'''
    if insert_after in text:
        text = text.replace(insert_after, insert_after + addition)
    else:
        text += '\n# ---- Code health report ----\n' + addition

if text != original:
    makefile.write_text(text)
PY

cat <<MSG
Done. Code health report target applied.
Backup directory: $BACKUP_DIR
Next step: make code-health
MSG
