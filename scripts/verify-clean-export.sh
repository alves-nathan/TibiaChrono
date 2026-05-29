#!/usr/bin/env bash
set -Eeuo pipefail

ZIP_PATH="${1:-TibiaChrono-clean.zip}"

if [[ ! -f "$ZIP_PATH" ]]; then
  echo "ERROR: export ZIP not found: $ZIP_PATH" >&2
  exit 1
fi

python3 - "$ZIP_PATH" <<'PY_VERIFY'
from pathlib import Path
import hashlib
import subprocess
import sys
import tempfile
import zipfile

zip_path = Path(sys.argv[1])
blocked_prefixes = (
    '.git/',
    '.idea/',
    '.test-maven/',
    'target/',
    'patches/',
)
blocked_suffixes = (
    '.bak',
    '.log',
    '.tmp',
    '.zip',
    'Zone.Identifier',
)

def read_zip(path: Path):
    with zipfile.ZipFile(path, 'r') as zf:
        names = sorted(name for name in zf.namelist() if not name.endswith('/'))
        contents = {name: hashlib.sha256(zf.read(name)).hexdigest() for name in names}
    return names, contents

names, contents = read_zip(zip_path)
violations = [
    name for name in names
    if name.startswith(blocked_prefixes) or name.endswith(blocked_suffixes)
]
if violations:
    print('ERROR: clean export contains excluded artifacts:', file=sys.stderr)
    for item in violations[:80]:
        print(f'- {item}', file=sys.stderr)
    if len(violations) > 80:
        print(f'- ... and {len(violations) - 80} more', file=sys.stderr)
    sys.exit(1)

inside_git = subprocess.run(
    ['git', 'rev-parse', '--is-inside-work-tree'],
    stdout=subprocess.DEVNULL,
    stderr=subprocess.DEVNULL,
).returncode == 0

if inside_git:
    with tempfile.TemporaryDirectory() as tmp:
        expected_zip = Path(tmp) / 'expected.zip'
        subprocess.run(
            ['git', 'archive', '--worktree-attributes', '--format=zip', f'--output={expected_zip}', 'HEAD'],
            check=True,
        )
        expected_names, expected_contents = read_zip(expected_zip)

    if names != expected_names:
        missing = sorted(set(expected_names) - set(names))
        extra = sorted(set(names) - set(expected_names))
        print('ERROR: clean export verification failed against git HEAD archive.', file=sys.stderr)
        if missing:
            print('- missing files:', ', '.join(missing[:50]), file=sys.stderr)
        if extra:
            print('- unexpected files:', ', '.join(extra[:50]), file=sys.stderr)
        sys.exit(1)

    mismatches = [name for name in names if contents[name] != expected_contents[name]]
    if mismatches:
        print('ERROR: clean export content mismatch against git HEAD archive.', file=sys.stderr)
        for item in mismatches[:50]:
            print(f'- {item}', file=sys.stderr)
        sys.exit(1)

print('Clean export verification passed.')
print(f'ZIP: {zip_path}')
print(f'Files verified: {len(names)}')
PY_VERIFY
