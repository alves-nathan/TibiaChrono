#!/usr/bin/env bash
set -euo pipefail

FILE="src/main/java/com/nathan/tibiastats/application/service/CharacterNamingService.java"

if [ ! -f "pom.xml" ] || [ ! -f "$FILE" ]; then
  echo "Execute este script na raiz do projeto TibiaChrono, onde fica o pom.xml." >&2
  exit 1
fi

BACKUP_DIR=".tibiachrono-fix-character-naming-compile-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR/$(dirname "$FILE")"
cp -a "$FILE" "$BACKUP_DIR/$FILE"

python3 - <<'PY'
from pathlib import Path
import re

path = Path('src/main/java/com/nathan/tibiastats/application/service/CharacterNamingService.java')
text = path.read_text()

old = '''    @Transactional
    public CharacterEntity reconcileOfficialNames(String currentName, List<String> formerNames) {
        currentName = CharacterNameNormalizer.normalize(currentName);
        formerNames = CharacterNameNormalizer.normalizeMany(formerNames);
        if (currentName == null || currentName.isBlank()) {
            return character;
        }
        return reconcileOfficialNames(null, currentName, formerNames);
    }
'''
new = '''    @Transactional
    public CharacterEntity reconcileOfficialNames(String currentName, List<String> formerNames) {
        currentName = CharacterNameNormalizer.normalize(currentName);
        formerNames = CharacterNameNormalizer.normalizeMany(formerNames);
        if (currentName == null || currentName.isBlank()) {
            throw new IllegalArgumentException("Current character name cannot be blank after normalization");
        }
        return reconcileOfficialNames(null, currentName, formerNames);
    }
'''

if old in text:
    text = text.replace(old, new, 1)
elif 'return character;' in text:
    # Fallback: replace only the invalid return inside the overload without knownCharacter.
    pattern = re.compile(
        r'(public\s+CharacterEntity\s+reconcileOfficialNames\s*\(\s*String\s+currentName\s*,\s*List<String>\s+formerNames\s*\)\s*\{.*?if\s*\(\s*currentName\s*==\s*null\s*\|\|\s*currentName\.isBlank\(\)\s*\)\s*\{\s*)return\s+character\s*;',
        re.S
    )
    text, count = pattern.subn(r'\1throw new IllegalArgumentException("Current character name cannot be blank after normalization");', text, count=1)
    if count == 0:
        raise SystemExit('Não encontrei o trecho inválido "return character;" dentro do overload reconcileOfficialNames(String, List<String>).')
else:
    print('Nenhum "return character;" inválido encontrado. Nada para corrigir.')

path.write_text(text)
PY

echo "Correção aplicada. Backup criado em: $BACKUP_DIR"
echo "Valide com: docker compose -f docker-compose.dev.yml build app"
