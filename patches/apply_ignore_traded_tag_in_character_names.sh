#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

if [ ! -d "src/main/java" ]; then
  echo "ERRO: rode este script na raiz do projeto TibiaChrono." >&2
  exit 1
fi

BACKUP_DIR=".tibiachrono-ignore-traded-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"

backup_if_exists() {
  local file="$1"
  if [ -f "$file" ]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

backup_if_exists "src/main/java/com/nathan/tibiastats/domain/model/CharacterName.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/application/service/CharacterNamingService.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/application/service/ScrapeService.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/domain/port/CharacterDetailPort.java"
backup_if_exists "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupCharacterAdapter.java"

mkdir -p "src/main/java/com/nathan/tibiastats/domain/model"
cat > "src/main/java/com/nathan/tibiastats/domain/model/CharacterNameNormalizer.java" <<'JAVA'
package com.nathan.tibiastats.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Normalizes raw character names scraped from Tibia.com.
 *
 * Tibia may append UI/status tags such as "(traded)" next to a character name.
 * Those tags are metadata and must not become part of character_names.name.
 */
public final class CharacterNameNormalizer {
    private static final Pattern TRADED_SUFFIX = Pattern.compile("\\s*\\((?i:traded)\\)\\s*$");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    private CharacterNameNormalizer() {}

    public static String normalize(String rawName) {
        if (rawName == null) {
            return null;
        }

        String normalized = rawName
                .replace('\u00A0', ' ')
                .trim();

        // Remove repeated suffixes defensively: "Name (traded) (traded)" -> "Name".
        String previous;
        do {
            previous = normalized;
            normalized = TRADED_SUFFIX.matcher(normalized).replaceAll("").trim();
        } while (!Objects.equals(previous, normalized));

        normalized = MULTIPLE_SPACES.matcher(normalized).replaceAll(" ").trim();
        return normalized;
    }

    public static boolean isBlank(String rawName) {
        String normalized = normalize(rawName);
        return normalized == null || normalized.isBlank();
    }

    public static boolean sameName(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);

        if (normalizedLeft == null || normalizedRight == null) {
            return normalizedLeft == normalizedRight;
        }

        return normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    public static List<String> normalizeMany(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalizedNames = new ArrayList<>();
        for (String rawName : rawNames) {
            String normalized = normalize(rawName);
            if (normalized != null && !normalized.isBlank()) {
                normalizedNames.add(normalized);
            }
        }
        return normalizedNames;
    }

    public static List<String> normalizeCsvToList(String rawNames) {
        if (rawNames == null || rawNames.isBlank()) {
            return Collections.emptyList();
        }

        List<String> normalizedNames = new ArrayList<>();
        for (String rawName : rawNames.split(",")) {
            String normalized = normalize(rawName);
            if (normalized != null && !normalized.isBlank()) {
                normalizedNames.add(normalized);
            }
        }
        return normalizedNames;
    }

    public static String normalizeCsv(String rawNames) {
        return String.join(",", normalizeCsvToList(rawNames));
    }

    public static String normalizedKey(String rawName) {
        String normalized = normalize(rawName);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
JAVA

python3 - <<'PY'
from pathlib import Path
import re

root = Path('.')

def read(path):
    return path.read_text(encoding='utf-8')

def write(path, text):
    path.write_text(text, encoding='utf-8')

# 1) CharacterName: normalize before persistence and whenever setName is called.
path = root / 'src/main/java/com/nathan/tibiastats/domain/model/CharacterName.java'
if path.exists():
    text = read(path)
    text = text.replace('this.name = name;', 'this.name = CharacterNameNormalizer.normalize(name);')

    if '@PrePersist' not in text and 'normalizeNameBeforePersistence' not in text:
        insert = '''\n\n    @PrePersist\n    @PreUpdate\n    private void normalizeNameBeforePersistence() {\n        this.name = CharacterNameNormalizer.normalize(this.name);\n    }\n'''
        text = re.sub(r'\n}\s*$', insert + '\n}', text)
    write(path, text)

# 2) SpringCharacterRepository: normalize repository lookup parameters.
path = root / 'src/main/java/com/nathan/tibiastats/infrastructure/persistence/SpringCharacterRepository.java'
if path.exists():
    text = read(path)
    # wildcard import usually already covers it; add explicit only if needed and no wildcard exists.
    if 'CharacterNameNormalizer' not in text and 'com.nathan.tibiastats.domain.model.*' not in text:
        text = text.replace('import com.nathan.tibiastats.domain.model.CharacterEntity;\n', 'import com.nathan.tibiastats.domain.model.CharacterEntity;\nimport com.nathan.tibiastats.domain.model.CharacterNameNormalizer;\n')

    replacements = {
        'return names.findName(name);': 'return names.findName(CharacterNameNormalizer.normalize(name));',
        'return names.findByNameAndActiveTrue(name);': 'return names.findByNameAndActiveTrue(CharacterNameNormalizer.normalize(name));',
        'return chars.findByAnyName(name, cutoff);': 'return chars.findByAnyName(CharacterNameNormalizer.normalize(name), cutoff);',
    }
    for old, new in replacements.items():
        text = text.replace(old, new)

    # If queries were already customized, keep them; otherwise make lookup case-insensitive where possible.
    text = text.replace('WHERE n.name = :name', 'WHERE lower(n.name) = lower(:name)')
    text = text.replace('where cn.name = :name', 'where lower(cn.name) = lower(:name)')
    text = text.replace('findByNameAndActiveTrue(String name)', 'findByNameIgnoreCaseAndActiveTrue(String name)')
    text = text.replace('names.findByNameAndActiveTrue(', 'names.findByNameIgnoreCaseAndActiveTrue(')
    write(path, text)

# 3) CharacterNamingService: normalize inputs and prevent false rename when only (traded) differs.
path = root / 'src/main/java/com/nathan/tibiastats/application/service/CharacterNamingService.java'
if path.exists():
    text = read(path)
    if 'CharacterNameNormalizer' not in text:
        # Add import next to CharacterName import when possible.
        text = text.replace('import com.nathan.tibiastats.domain.model.CharacterName;\n', 'import com.nathan.tibiastats.domain.model.CharacterName;\nimport com.nathan.tibiastats.domain.model.CharacterNameNormalizer;\n')

    marker = 'public CharacterEntity ensureCharacterForName(String name, String formerNames){'
    if marker in text and 'name = CharacterNameNormalizer.normalize(name);' not in text:
        text = text.replace(marker, marker + '''\n        name = CharacterNameNormalizer.normalize(name);\n        formerNames = CharacterNameNormalizer.normalizeCsv(formerNames);\n        if (name == null || name.isBlank()) {\n            throw new IllegalArgumentException("Character name cannot be blank after normalization");\n        }\n''')

    # Fix known bug from older code: former-name loop was searching the current name instead of formerName.
    text = text.replace('var nameClass = repo.findName(name);', 'var nameClass = repo.findName(formerName);')

    marker = 'public void handleRenamed(CharacterEntity c, String newActiveName, CharacterName oldName){'
    if marker in text and 'newActiveName = CharacterNameNormalizer.normalize(newActiveName);' not in text:
        text = text.replace(marker, marker + '''\n        newActiveName = CharacterNameNormalizer.normalize(newActiveName);\n        if (newActiveName == null || newActiveName.isBlank()) {\n            return;\n        }\n        if (oldName == null || CharacterNameNormalizer.sameName(oldName.getName(), newActiveName)) {\n            return;\n        }\n''')

    # For services generated by earlier scripts with reconcileOfficialNames, normalize official inputs too.
    marker = 'public CharacterEntity reconcileOfficialNames('
    if marker in text and 'CharacterNameNormalizer.normalize(currentName)' not in text:
        # Insert after method opening brace of reconcileOfficialNames if recognizable.
        text = re.sub(
            r'(public CharacterEntity reconcileOfficialNames\([^)]*\)\s*\{)',
            r'\1\n        currentName = CharacterNameNormalizer.normalize(currentName);\n        formerNames = CharacterNameNormalizer.normalizeMany(formerNames);\n        if (currentName == null || currentName.isBlank()) {\n            return character;\n        }',
            text,
            count=1,
            flags=re.S
        )

    write(path, text)

# 4) ScrapeService: compare normalized names to avoid false rename call.
path = root / 'src/main/java/com/nathan/tibiastats/application/service/ScrapeService.java'
if path.exists():
    text = read(path)
    if 'CharacterNameNormalizer' not in text:
        text = text.replace('import com.nathan.tibiastats.domain.model.CharacterName;\n', 'import com.nathan.tibiastats.domain.model.CharacterName;\nimport com.nathan.tibiastats.domain.model.CharacterNameNormalizer;\n')
    text = text.replace('if(!name.getName().equals(playerName)) {', 'if(!CharacterNameNormalizer.sameName(name.getName(), playerName)) {')
    text = text.replace('if (!name.getName().equals(playerName)) {', 'if (!CharacterNameNormalizer.sameName(name.getName(), playerName)) {')
    write(path, text)

# 5) ScrapePort: normalize record values if the current source still has records here.
path = root / 'src/main/java/com/nathan/tibiastats/domain/port/ScrapePort.java'
if path.exists():
    text = read(path)
    if 'CharacterNameNormalizer' not in text:
        text = text.replace('import com.nathan.tibiastats.domain.model.World;\n', 'import com.nathan.tibiastats.domain.model.World;\nimport com.nathan.tibiastats.domain.model.CharacterNameNormalizer;\n')
    if 'record OnlineCharacter' in text and 'public OnlineCharacter {' not in text:
        text = re.sub(
            r'record OnlineCharacter\(([^)]*)\) \{}',
            r'record OnlineCharacter(\1) {\n        public OnlineCharacter {\n            name = CharacterNameNormalizer.normalize(name);\n        }\n    }',
            text
        )
    write(path, text)

# 6) CharacterDetailPort: normalize details records where they exist.
path = root / 'src/main/java/com/nathan/tibiastats/domain/port/CharacterDetailPort.java'
if path.exists():
    text = read(path)
    if 'CharacterNameNormalizer' not in text:
        text = text.replace('package com.nathan.tibiastats.domain.port;\n', 'package com.nathan.tibiastats.domain.port;\n\nimport com.nathan.tibiastats.domain.model.CharacterNameNormalizer;')
    # Fix import formatting if no blank line existed.
    text = text.replace('CharacterNameNormalizer;import', 'CharacterNameNormalizer;\nimport')

    if 'record NameDetails' in text and 'public NameDetails {' not in text:
        text = re.sub(
            r'record NameDetails\(String currentName, List<String> formerNames\) \{}',
            'record NameDetails(String currentName, List<String> formerNames) {\n        public NameDetails {\n            currentName = CharacterNameNormalizer.normalize(currentName);\n            formerNames = CharacterNameNormalizer.normalizeMany(formerNames);\n        }\n    }',
            text
        )
    # Common name used in generated detail scheduler patches. Only add a compact
    # constructor when the record explicitly has currentName and formerNames fields.
    match = re.search(r'record CharacterDetails\(([^)]*)\) \{}', text)
    if match and 'public CharacterDetails {' not in text:
        record_fields = match.group(1)
        if 'currentName' in record_fields and 'formerNames' in record_fields:
            text = text[:match.start()] + (
                'record CharacterDetails(' + record_fields + ') {\n'
                '        public CharacterDetails {\n'
                '            currentName = CharacterNameNormalizer.normalize(currentName);\n'
                '            formerNames = CharacterNameNormalizer.normalizeMany(formerNames);\n'
                '        }\n'
                '    }'
            ) + text[match.end():]
    write(path, text)

# 7) JsoupScrapeAdapter: normalize player names and former-name CSV at the scraper boundary.
path = root / 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java'
if path.exists():
    text = read(path)
    if 'CharacterNameNormalizer' not in text:
        text = text.replace('import com.nathan.tibiastats.domain.model.World;\n', 'import com.nathan.tibiastats.domain.model.World;\nimport com.nathan.tibiastats.domain.model.CharacterNameNormalizer;\n')
    text = text.replace('players.add(nameTd);', '''String normalizedName = CharacterNameNormalizer.normalize(nameTd);\n                    if (normalizedName != null && !normalizedName.isBlank()) {\n                        players.add(normalizedName);\n                    }''')
    text = text.replace('return line.select("td:last-child").text();', 'return CharacterNameNormalizer.normalizeCsv(line.select("td:last-child").text());')
    text = text.replace('return name;', 'return CharacterNameNormalizer.normalize(name);')
    write(path, text)

# 8) JsoupCharacterAdapter: normalize fallback NameDetails output in older implementation.
path = root / 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupCharacterAdapter.java'
if path.exists():
    text = read(path)
    if 'CharacterNameNormalizer' not in text:
        text = text.replace('import com.nathan.tibiastats.domain.port.CharacterDetailPort;\n', 'import com.nathan.tibiastats.domain.port.CharacterDetailPort;\nimport com.nathan.tibiastats.domain.model.CharacterNameNormalizer;\n')
    text = text.replace('return new NameDetails(characterName, List.of());', 'return new NameDetails(CharacterNameNormalizer.normalize(characterName), List.of());')
    write(path, text)

PY

mkdir -p src/main/resources/db/manual
cat > src/main/resources/db/manual/cleanup_traded_character_names.sql <<'SQL'
-- Find rows currently polluted by the Tibia.com UI tag "(traded)".
select id, character_id, name, active, inactive_date
from character_names
where name ~* '\\s*\\(traded\\)\\s*$'
order by character_id, active desc, name;

-- Safe cleanup: rename only rows whose normalized name does not collide with another row.
update character_names cn
set name = trim(regexp_replace(cn.name, '\\s*\\(traded\\)\\s*$', '', 'i'))
where cn.name ~* '\\s*\\(traded\\)\\s*$'
  and not exists (
      select 1
      from character_names other_cn
      where other_cn.id <> cn.id
        and lower(other_cn.name) = lower(trim(regexp_replace(cn.name, '\\s*\\(traded\\)\\s*$', '', 'i')))
  );

-- Rows returned here need manual/merge review because the normalized name already exists elsewhere.
select
    cn.id,
    cn.character_id,
    cn.name as polluted_name,
    trim(regexp_replace(cn.name, '\\s*\\(traded\\)\\s*$', '', 'i')) as normalized_name,
    existing.id as existing_name_id,
    existing.character_id as existing_character_id,
    existing.active as existing_active
from character_names cn
join character_names existing
  on existing.id <> cn.id
 and lower(existing.name) = lower(trim(regexp_replace(cn.name, '\\s*\\(traded\\)\\s*$', '', 'i')))
where cn.name ~* '\\s*\\(traded\\)\\s*$'
order by cn.character_id, cn.name;
SQL

echo "Correção aplicada. Backup em: $BACKUP_DIR"
echo "Arquivo SQL opcional criado em: src/main/resources/db/manual/cleanup_traded_character_names.sql"
