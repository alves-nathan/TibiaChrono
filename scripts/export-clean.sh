#!/usr/bin/env bash
set -Eeuo pipefail

OUTPUT="${1:-TibiaChrono-clean.zip}"
ROOT_DIR="$(pwd)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

rm -f "$OUTPUT"

if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git archive --format=zip --output="$OUTPUT" HEAD
  echo "Created clean export from git HEAD: $OUTPUT"
  exit 0
fi

python3 - "$OUTPUT" <<'PY_EXPORT'
from pathlib import Path
import fnmatch
import sys
import zipfile

output = Path(sys.argv[1])
excludes = [
    '.git/*', '.idea/*', '.test-maven/*', 'target/*',
    'patches/.backups/*', '*.bak', '*.bak-*', '*.bak.*',
    '*Zone.Identifier*', '*.log', output.name,
]

with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as zf:
    for path in Path('.').rglob('*'):
        if path.is_dir():
            continue
        rel = path.as_posix()
        if rel.startswith('./'):
            rel = rel[2:]
        if any(fnmatch.fnmatch(rel, pattern) for pattern in excludes):
            continue
        zf.write(path, rel)

print(f'Created clean export: {output}')
PY_EXPORT
