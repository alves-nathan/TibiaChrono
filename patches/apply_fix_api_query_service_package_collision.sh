#!/usr/bin/env bash
set -euo pipefail

if [ ! -f "pom.xml" ] || [ ! -d "src/main/java/com/nathan/tibiastats" ]; then
  echo "ERROR: run this patch from the TibiaChrono project root." >&2
  exit 1
fi

QUERY_SERVICE="src/main/java/com/nathan/tibiastats/application/query/ApiQueryService.java"
LEGACY_SERVICE="src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java"
BACKUP_DIR="patches/.backups/api-query-service-package-collision-$(date +%Y%m%d%H%M%S)"

if [ ! -f "$QUERY_SERVICE" ]; then
  echo "ERROR: expected query-side ApiQueryService was not found:" >&2
  echo "  $QUERY_SERVICE" >&2
  echo "This patch targets the architecture-cleanup state where ApiQueryService lives in application.query." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

if [ -f "$LEGACY_SERVICE" ]; then
  cp "$LEGACY_SERVICE" "$BACKUP_DIR/ApiQueryService.java.bak"
  rm "$LEGACY_SERVICE"
  echo "Removed legacy/service ApiQueryService that was shadowing application.query.ApiQueryService."
else
  echo "No legacy/service ApiQueryService found. Continuing with import normalization."
fi

python3 - <<'PY'
from pathlib import Path

root = Path("src/main/java/com/nathan/tibiastats")
query_import = "import com.nathan.tibiastats.application.query.ApiQueryService;"
old_import = "import com.nathan.tibiastats.application.service.ApiQueryService;"

# Files that failed before the legacy facade was restored, and any future service that still
# references ApiQueryService from the same package by simple name.
candidates = [
    Path("src/main/java/com/nathan/tibiastats/application/service/CharacterOnlineActivityService.java"),
    Path("src/main/java/com/nathan/tibiastats/application/service/AdminScraperService.java"),
]

for extra in Path("src/main/java/com/nathan/tibiastats/application/service").glob("*.java"):
    if extra not in candidates:
        candidates.append(extra)

changed = []
for path in candidates:
    if not path.exists() or path.name == "ApiQueryService.java":
        continue
    text = path.read_text()
    if "ApiQueryService" not in text:
        continue

    original = text
    text = text.replace(old_import, query_import)

    # In application.service package, the old ApiQueryService used to be visible without an import.
    # After deleting it, explicitly import the query-side service.
    if query_import not in text:
        package_line = "package com.nathan.tibiastats.application.service;\n"
        if package_line not in text:
            continue
        text = text.replace(package_line, package_line + "\n" + query_import + "\n", 1)

    # Keep a single blank line between package/import blocks and avoid duplicate imports.
    lines = text.splitlines()
    seen = False
    normalized = []
    for line in lines:
        if line == query_import:
            if seen:
                continue
            seen = True
        normalized.append(line)
    text = "\n".join(normalized) + ("\n" if original.endswith("\n") else "")

    if text != original:
        path.write_text(text)
        changed.append(str(path))

if changed:
    print("Updated ApiQueryService imports in:")
    for item in changed:
        print(f"  - {item}")
else:
    print("No service imports required updates.")
PY

# Defensive check: the package collision must be gone.
if [ -f "$LEGACY_SERVICE" ]; then
  echo "ERROR: legacy/service ApiQueryService still exists after patch." >&2
  exit 1
fi

if grep -R "com.nathan.tibiastats.application.service.ApiQueryService" -n src/main/java src/test/java >/tmp/api_query_old_imports.$$ 2>/dev/null; then
  echo "ERROR: old ApiQueryService imports remain:" >&2
  cat /tmp/api_query_old_imports.$$ >&2
  rm -f /tmp/api_query_old_imports.$$
  exit 1
fi
rm -f /tmp/api_query_old_imports.$$

echo "Patch applied. Backup directory: $BACKUP_DIR"
echo "Next step: make test"
