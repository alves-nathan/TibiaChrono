#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

TARGET="src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java"

if [[ ! -f "pom.xml" ]] || [[ ! -f "$TARGET" ]]; then
  echo "ERROR: execute este script na raiz do projeto TibiaChrono." >&2
  echo "Arquivo esperado não encontrado: $TARGET" >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path

path = Path("src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java")
text = path.read_text()
original = text

# The characterization fixture and real tibia.com links can use either:
#   ?name=Character+Name
# or:
#   ?subtopic=characters&name=Character+Name
# The old selector a[href*=?name=] only matches the first shape and misses the second.
text = text.replace('a[href*=?name=]', 'a[href*=name=]')

# Some fixture snippets do not include the exact same InnerTableContainer nesting as the live page.
# Select Table2 rows more broadly, then filter non-data/layout rows by requiring the data columns.
text = text.replace(
    'Elements rowsT2 = doc.select("table.Table2 div.InnerTableContainer tr");',
    'Elements rowsT2 = doc.select("table.Table2 div.InnerTableContainer tr, table.Table2 tr");'
)

old = '''                Elements cols = tr.select("> td");
                if (cols.isEmpty()) {
                    continue;
                }

                String name = tr.select("a[href*=name=]").text().trim();
                if (name.isBlank()) {
                    continue;
                }

                Integer level = cols.size() > 1 ? parseIntegerOrNull(cols.get(1).text()) : null;
                String vocation = cols.size() > 2 ? blankToNull(cols.get(2).text()) : null;
'''
new = '''                Elements cols = tr.select("> td");
                if (cols.size() < 3) {
                    continue;
                }

                String name = tr.select("a[href*=name=]").text().trim();
                if (name.isBlank()) {
                    name = cols.get(0).text().trim();
                }
                if (name.isBlank() || name.equalsIgnoreCase("Name") || name.contains("[sort]")) {
                    continue;
                }

                Integer level = parseIntegerOrNull(cols.get(1).text());
                String vocation = blankToNull(cols.get(2).text());
'''

if old in text:
    text = text.replace(old, new, 1)
else:
    # Fallback for partially modified versions: tighten the column guard and add a plain-text name fallback.
    text = text.replace('if (cols.isEmpty()) {\n                    continue;\n                }',
                        'if (cols.size() < 3) {\n                    continue;\n                }', 1)
    text = text.replace('String name = tr.select("a[href*=name=]").text().trim();\n                if (name.isBlank()) {\n                    continue;\n                }',
                        'String name = tr.select("a[href*=name=]").text().trim();\n                if (name.isBlank()) {\n                    name = cols.get(0).text().trim();\n                }\n                if (name.isBlank() || name.equalsIgnoreCase("Name") || name.contains("[sort]")) {\n                    continue;\n                }', 1)
    text = text.replace('Integer level = cols.size() > 1 ? parseIntegerOrNull(cols.get(1).text()) : null;\n                String vocation = cols.size() > 2 ? blankToNull(cols.get(2).text()) : null;',
                        'Integer level = parseIntegerOrNull(cols.get(1).text());\n                String vocation = blankToNull(cols.get(2).text());', 1)

if text == original:
    raise SystemExit("ERROR: nenhuma alteração aplicada. O JsoupScrapeAdapter.java atual não contém o trecho esperado do parser de players online.")

if 'a[href*=?name=]' in text:
    raise SystemExit("ERROR: ainda existe seletor restritivo a[href*=?name=] no JsoupScrapeAdapter.java.")

path.write_text(text)
print("JsoupScrapeAdapter atualizado para reconhecer links de personagens com &name= e filtrar linhas de dados online.")
PY

echo "Patch aplicado. Próximo passo sugerido:"
echo "  make test"
