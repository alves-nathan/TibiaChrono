#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="repair-query-read-model-annotations"
ROOT_DIR="$(pwd)"
BACKUP_DIR="patches/.backups/${PATCH_NAME}-$(date +%Y%m%d%H%M%S)"
QUERY_DIR="src/main/java/com/nathan/tibiastats/application/query"
ANNOTATION_FILE="${QUERY_DIR}/ReadModelComponent.java"

if [[ ! -d "src/main/java/com/nathan/tibiastats" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

if [[ ! -d "$QUERY_DIR" ]]; then
  echo "ERROR: $QUERY_DIR does not exist. Apply apply_query_model_package_boundary_v3.sh first." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

backup_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    local dest="$BACKUP_DIR/$file"
    mkdir -p "$(dirname "$dest")"
    cp "$file" "$dest"
  fi
}

backup_file "$ANNOTATION_FILE"
find "$QUERY_DIR" -maxdepth 1 -type f -name '*Service.java' -print0 | while IFS= read -r -d '' file; do
  backup_file "$file"
done

cat > "$ANNOTATION_FILE" <<'EOF'
package com.nathan.tibiastats.application.query;

import org.springframework.stereotype.Service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Service
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadModelComponent {
}
EOF

python3 - <<'PY'
from pathlib import Path
import re

query_dir = Path("src/main/java/com/nathan/tibiastats/application/query")
service_files = sorted(query_dir.glob("*Service.java"))

if not service_files:
    raise SystemExit("ERROR: no *Service.java files found under application/query")

changed = []
for path in service_files:
    text = path.read_text()
    original = text

    # Query services should use the semantic read-model stereotype instead of the generic Spring one.
    text = re.sub(r"(?m)^import\s+org\.springframework\.stereotype\.Service;\s*\n", "", text)

    if "@ReadModelComponent" not in text:
        # Prefer replacing a class-level @Service if one is present.
        text, count = re.subn(
            r"(?m)^(\s*)@Service\s*\n(?=\s*(?:@[\w.]+(?:\([^\n]*\))?\s*\n)*\s*(?:public\s+)?(?:final\s+)?class\s+)",
            r"\1@ReadModelComponent\n",
            text,
            count=1,
        )

        if count == 0:
            # Otherwise insert immediately before the class declaration, preserving any other annotations above it.
            text, count = re.subn(
                r"(?m)^(\s*)((?:public\s+)?(?:final\s+)?class\s+\w+)",
                r"\1@ReadModelComponent\n\1\2",
                text,
                count=1,
            )

        if count == 0:
            raise SystemExit(f"ERROR: could not locate class declaration in {path}")

    if text != original:
        path.write_text(text)
        changed.append(str(path))

print("Read-model services checked:")
for path in service_files:
    print(f" - {path}")
print("Changed files:")
if changed:
    for path in changed:
        print(f" - {path}")
else:
    print(" - none; files were already annotated")
PY

# Normalize permissions: Java sources should not be executable.
find src/main/java src/test/java -type f -name '*.java' -exec chmod 0644 {} +
chmod 0755 "$0" 2>/dev/null || true

echo "Done. Query read-model annotations repaired."
echo "Backup directory: $BACKUP_DIR"
echo "Next step: make qa"
