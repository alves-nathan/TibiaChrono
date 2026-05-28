#!/usr/bin/env bash
set -Eeuo pipefail

BACKUP_SUFFIX=".bak-repository-hygiene-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

backup_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    cp "$file" "$file$BACKUP_SUFFIX"
  fi
}

backup_file ".gitignore"
backup_file "Makefile"

python3 - <<'PY'
from pathlib import Path

root = Path('.')

def ensure_lines(path: Path, lines: list[str]) -> None:
    text = path.read_text() if path.exists() else ''
    original = text
    if text and not text.endswith('\n'):
        text += '\n'
    missing = [line for line in lines if line not in text.splitlines()]
    if missing:
        if '### TibiaChrono local/generated artifacts ###' not in text:
            text += '\n### TibiaChrono local/generated artifacts ###\n'
        for line in missing:
            text += f'{line}\n'
    if text != original:
        path.write_text(text)

ensure_lines(Path('.gitignore'), [
    '/.test-maven/',
    '/target/',
    '/.idea/',
    'patches/.backups/',
    '*.bak',
    '*.bak-*',
    '*.bak.*',
    '*Zone.Identifier*',
    'TibiaChrono-clean.zip',
])

scripts_dir = Path('scripts')
scripts_dir.mkdir(exist_ok=True)
export_script = scripts_dir / 'export-clean.sh'
export_script.write_text('''#!/usr/bin/env bash
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
''')
export_script.chmod(0o755)

makefile = Path('Makefile')
text = makefile.read_text()
original = text

help_line = '\t@echo "  make audit-worktree - list local/generated artifacts that should not be committed"\n\t@echo "  make clean-local-artifacts - remove local/generated artifacts from the working tree"\n\t@echo "  make export-clean   - create a clean ZIP export without local artifacts"'
if 'make audit-worktree' not in text:
    marker = '\t@echo "  make env-print     - show important env vars"'
    text = text.replace(marker, marker + '\n' + help_line)

if '.PHONY: audit-worktree' not in text:
    text += r'''

# ---- Repository hygiene ----
.PHONY: audit-worktree
 audit-worktree:
	@echo "Local/generated artifacts currently present:"; \
	find . \
	  -path './.git' -prune -o \
	  -path './.idea' -print -prune -o \
	  -path './.test-maven' -print -prune -o \
	  -path './target' -print -prune -o \
	  -path './patches/.backups' -print -prune -o \
	  \( -name '*.bak' -o -name '*.bak-*' -o -name '*.bak.*' -o -name '*Zone.Identifier*' \) -print | sort

.PHONY: clean-local-artifacts
 clean-local-artifacts:
	@echo "Removing local/generated artifacts..."; \
	rm -rf .idea .test-maven target patches/.backups; \
	find . \
	  -path './.git' -prune -o \
	  \( -name '*.bak' -o -name '*.bak-*' -o -name '*.bak.*' -o -name '*Zone.Identifier*' \) -type f -print0 | xargs -0 -r rm -f; \
	echo "Done."

.PHONY: export-clean
 export-clean:
	./scripts/export-clean.sh
'''

# Fix possible accidental leading spaces before target names in the raw block above.
text = text.replace('\n audit-worktree:', '\naudit-worktree:')
text = text.replace('\n clean-local-artifacts:', '\nclean-local-artifacts:')
text = text.replace('\n export-clean:', '\nexport-clean:')

if text != original:
    makefile.write_text(text)
PY

# Remove artifacts that should not be carried forward in clean project snapshots.
rm -rf .idea .test-maven target patches/.backups
python3 - <<'PY_CLEAN_ARTIFACTS'
from pathlib import Path
import fnmatch

patterns = ['*.bak', '*.bak-*', '*.bak.*', '*Zone.Identifier*']
for path in Path('.').rglob('*'):
    if '.git' in path.parts:
        continue
    if path.is_file() and any(fnmatch.fnmatch(path.name, pattern) for pattern in patterns):
        path.unlink()
PY_CLEAN_ARTIFACTS

cat <<'MSG'

Done.
Repository hygiene applied.

Next steps:
  make audit-worktree
  make export-clean

This patch intentionally does not delete .git because local repository metadata belongs to your working copy.
MSG
