#!/usr/bin/env bash
set -euo pipefail

if [ ! -f "pom.xml" ] || [ ! -d "src/main/java/com/nathan/tibiastats" ]; then
  echo "ERROR: run this patch from the TibiaChrono project root." >&2
  exit 1
fi

TARGET="src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java"

if [ ! -f "$TARGET" ]; then
  echo "ERROR: expected file not found: $TARGET" >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path

path = Path("src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java")
text = path.read_text()
original = text

if "findCharacterOnline" not in text and "CharacterOnline" not in text and "online-history" not in text:
    raise SystemExit(
        "ERROR: ApiQueryService.java does not seem to contain the character online activity feature. "
        "Apply this patch on the updated codebase that contains that feature."
    )


def add_import(source: str, import_line: str) -> str:
    if import_line in source:
        return source
    lines = source.splitlines()
    package_idx = None
    import_indices = []
    for i, line in enumerate(lines):
        if line.startswith("package "):
            package_idx = i
        if line.startswith("import "):
            import_indices.append(i)
    if import_indices:
        insert_at = import_indices[-1] + 1
    elif package_idx is not None:
        insert_at = package_idx + 1
        if insert_at < len(lines) and lines[insert_at].strip() == "":
            insert_at += 1
    else:
        insert_at = 0
    lines.insert(insert_at, import_line)
    return "\n".join(lines) + ("\n" if source.endswith("\n") else "")

text = add_import(text, "import java.sql.Types;")
text = add_import(text, "import java.time.OffsetDateTime;")
text = add_import(text, "import java.time.ZoneOffset;")
text = add_import(text, "import java.time.Instant;")


def rewrite_named_add_value_calls(source: str) -> tuple[str, int]:
    targets = ['.addValue("from",', '.addValue("to",']
    out = []
    i = 0
    changed = 0
    n = len(source)

    while i < n:
        matches = [(source.find(token, i), token) for token in targets]
        matches = [(pos, token) for pos, token in matches if pos != -1]
        if not matches:
            out.append(source[i:])
            break

        pos, token = min(matches, key=lambda item: item[0])
        out.append(source[i:pos])

        open_paren = source.find("(", pos)
        if open_paren == -1:
            out.append(source[pos:])
            break

        depth = 0
        in_string = False
        escape = False
        end = None
        for j in range(open_paren, n):
            ch = source[j]
            if in_string:
                if escape:
                    escape = False
                elif ch == "\\":
                    escape = True
                elif ch == '"':
                    in_string = False
                continue
            if ch == '"':
                in_string = True
                continue
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    end = j
                    break

        if end is None:
            out.append(source[pos:])
            break

        call = source[pos:end + 1]
        if "Types.TIMESTAMP_WITH_TIMEZONE" in call or "toSqlTimestamp(" in call:
            out.append(call)
            i = end + 1
            continue

        # call example: .addValue("from", filter.from())
        first_comma = call.find(",")
        if first_comma == -1:
            out.append(call)
            i = end + 1
            continue

        name_arg = call[len(".addValue("):first_comma].strip()
        if name_arg not in {'"from"', '"to"'}:
            out.append(call)
            i = end + 1
            continue

        expr = call[first_comma + 1:-1].strip()
        if not expr:
            out.append(call)
            i = end + 1
            continue

        rewritten = f'.addValue({name_arg}, toSqlTimestamp({expr}), Types.TIMESTAMP_WITH_TIMEZONE)'
        out.append(rewritten)
        changed += 1
        i = end + 1

    return "".join(out), changed

text, rewrites = rewrite_named_add_value_calls(text)

if "private static OffsetDateTime toSqlTimestamp(Instant instant)" not in text:
    constructor_start = text.find("public ApiQueryService(")
    if constructor_start == -1:
        raise SystemExit("ERROR: could not locate ApiQueryService constructor to insert helper method.")
    open_brace = text.find("{", constructor_start)
    if open_brace == -1:
        raise SystemExit("ERROR: could not locate ApiQueryService constructor body.")
    depth = 0
    constructor_end = None
    for idx in range(open_brace, len(text)):
        ch = text[idx]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                constructor_end = idx + 1
                break
    if constructor_end is None:
        raise SystemExit("ERROR: could not locate end of ApiQueryService constructor.")

    helper = """

    private static OffsetDateTime toSqlTimestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
"""
    text = text[:constructor_end] + helper + text[constructor_end:]

if text == original:
    print("Patch already applied or no matching Instant parameters were found.")
else:
    path.write_text(text)
    print(f"Updated {path}: {rewrites} named from/to parameter binding(s) now use explicit TIMESTAMP_WITH_TIMEZONE typing.")
PY

echo "Patch applied: character online activity SQL parameters now bind Instant values with an explicit PostgreSQL timestamp type."
echo "Run: make test"
echo "Then: make test-coverage"
