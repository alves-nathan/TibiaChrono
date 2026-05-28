#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

TARGET="src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java"

if [[ ! -f "pom.xml" ]] || [[ ! -f "$TARGET" ]]; then
  echo "ERROR: execute este script na raiz do projeto TibiaChrono." >&2
  echo "Arquivo esperado não encontrado: $TARGET" >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path
import re

path = Path("src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java")
text = path.read_text()
original = text

# Ensure imports required by prepareParams are available.
if "import java.sql.Timestamp;" not in text:
    text = text.replace("import java.sql.SQLException;\n", "import java.sql.SQLException;\nimport java.sql.Timestamp;\n", 1)
if "import java.sql.Types;" not in text:
    text = text.replace("import java.sql.Timestamp;\n", "import java.sql.Timestamp;\nimport java.sql.Types;\n", 1)
if "import java.util.ArrayList;" not in text:
    if "import java.util.List;" in text:
        text = text.replace("import java.util.List;\n", "import java.util.ArrayList;\nimport java.util.List;\n", 1)
    else:
        text = text.replace("import java.time.Instant;\n", "import java.time.Instant;\nimport java.util.ArrayList;\n", 1)

helper = '''
    private MapSqlParameterSource prepareParams(MapSqlParameterSource params) {
        if (params == null) {
            return new MapSqlParameterSource();
        }

        for (var entry : new ArrayList<>(params.getValues().entrySet())) {
            if (entry.getValue() instanceof Instant instant) {
                params.addValue(entry.getKey(), Timestamp.from(instant), Types.TIMESTAMP);
            }
        }

        return params;
    }
'''

if "private MapSqlParameterSource prepareParams(MapSqlParameterSource params)" not in text:
    marker = "    private int safeLimit(int requested) {\n"
    if marker in text:
        text = text.replace(marker, helper + "\n" + marker, 1)
    else:
        # Fallback: place before the first mapper method, which should exist in this service.
        marker = "    private WorldView mapWorld("
        if marker not in text:
            raise SystemExit("ERROR: não foi possível encontrar um ponto seguro para inserir prepareParams().")
        text = text.replace(marker, helper + "\n" + marker, 1)

# Replace only jdbc.query(...) invocations whose SqlParameterSource argument is the local variable `params`.
# This avoids corrupting helper calls such as appendHighscoreFilters(sql, params, ...).
def replace_query_params(match: re.Match) -> str:
    invocation = match.group(0)
    if "prepareParams(params)" in invocation:
        return invocation
    return re.sub(r"(?<!prepareParams\()\bparams\b", "prepareParams(params)", invocation, count=1)

# Handles single-line and multi-line invocations ending at the first semicolon.
text = re.sub(r"jdbc\.query\((?:(?!;).)*?\bparams\b(?:(?!;).)*?;", replace_query_params, text, flags=re.DOTALL)

# If any previous partial patch left addValue(..., toSqlTimestamp(...)) without SQL type, keep it typed.
text = re.sub(
    r'\.addValue\(\s*"(from|to)"\s*,\s*toSqlTimestamp\(\s*\1\s*\)\s*\)',
    r'.addValue("\1", toSqlTimestamp(\1), Types.TIMESTAMP)',
    text,
    flags=re.DOTALL,
)

# Final safety check: no jdbc.query call should still pass bare params as an argument.
remaining = []
for m in re.finditer(r"jdbc\.query\((?:(?!;).)*?;", text, flags=re.DOTALL):
    call = m.group(0)
    if re.search(r"[,\n]\s*params\s*[,\n)]", call) and "prepareParams(params)" not in call:
        remaining.append(call[:240].replace("\n", " "))

if remaining:
    raise SystemExit("ERROR: ainda há jdbc.query(...) usando params sem prepareParams():\n" + "\n".join(remaining))

if text == original:
    print("Patch já parece aplicado. Nada a alterar.")
else:
    path.write_text(text)
    print("ApiQueryService atualizado para sanitizar parâmetros Instant antes das queries JDBC.")
PY

echo "Patch aplicado. Próximo passo sugerido:"
echo "  make test"
echo "Se passar:"
echo "  make test-coverage"
