#!/usr/bin/env bash
set -Eeuo pipefail

OUTPUT="${1:-TibiaChrono-clean.zip}"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

rm -f "$OUTPUT"

if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if ! git diff --quiet --ignore-submodules -- . ':!patches' || ! git diff --cached --quiet --ignore-submodules -- . ':!patches'; then
    echo "WARNING: exporting from git HEAD. Uncommitted tracked changes will not be included." >&2
    echo "Commit the current state before running make export-clean if you want those changes in the ZIP." >&2
  fi

  # --worktree-attributes lets the current .gitattributes exclude operational files
  # such as patches/ even before the .gitattributes change itself is committed.
  git archive --worktree-attributes --format=zip --output="$OUTPUT" HEAD
  echo "Created clean export from git HEAD: $OUTPUT"
  echo "Snapshot source: git HEAD. Commit before exporting to include recent patch changes."
  echo "Excluded by export attributes: patches/, local artifacts, generated outputs."
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
    'patches/*', 'patches/.backups/*', '*.bak', '*.bak-*', '*.bak.*',
    '*Zone.Identifier*', '*.log', '*.tmp', '*.zip', output.name,
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

print(f'Created clean export from working tree fallback: {output}')
PY_EXPORT
