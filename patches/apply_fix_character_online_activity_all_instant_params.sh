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

if "findCharacterOnline" not in text and "CharacterOnline" not in text:
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


def find_matching_paren(source: str, open_idx: int) -> int | None:
    depth = 0
    in_string = False
    in_char = False
    escape = False
    line_comment = False
    block_comment = False
    i = open_idx
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""

        if line_comment:
            if ch == "\n":
                line_comment = False
            i += 1
            continue
        if block_comment:
            if ch == "*" and nxt == "/":
                block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            i += 1
            continue
        if in_char:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == "'":
                in_char = False
            i += 1
            continue

        if ch == "/" and nxt == "/":
            line_comment = True
            i += 2
            continue
        if ch == "/" and nxt == "*":
            block_comment = True
            i += 2
            continue
        if ch == '"':
            in_string = True
            i += 1
            continue
        if ch == "'":
            in_char = True
            i += 1
            continue
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return None


def split_top_level_args(args: str) -> list[str]:
    result = []
    start = 0
    depth = 0
    in_string = False
    in_char = False
    escape = False
    line_comment = False
    block_comment = False
    i = 0
    while i < len(args):
        ch = args[i]
        nxt = args[i + 1] if i + 1 < len(args) else ""

        if line_comment:
            if ch == "\n":
                line_comment = False
            i += 1
            continue
        if block_comment:
            if ch == "*" and nxt == "/":
                block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            i += 1
            continue
        if in_char:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == "'":
                in_char = False
            i += 1
            continue

        if ch == "/" and nxt == "/":
            line_comment = True
            i += 2
            continue
        if ch == "/" and nxt == "*":
            block_comment = True
            i += 2
            continue
        if ch == '"':
            in_string = True
            i += 1
            continue
        if ch == "'":
            in_char = True
            i += 1
            continue
        if ch in "([{":
            depth += 1
        elif ch in ")]}" and depth > 0:
            depth -= 1
        elif ch == "," and depth == 0:
            result.append(args[start:i].strip())
            start = i + 1
        i += 1
    tail = args[start:].strip()
    if tail:
        result.append(tail)
    return result


def rewrite_calls(source: str, method_name: str) -> tuple[str, int]:
    out = []
    i = 0
    changed = 0
    n = len(source)
    needle = method_name + "("

    while i < n:
        pos = source.find(needle, i)
        if pos == -1:
            out.append(source[i:])
            break

        # Avoid matching longer identifiers such as someAddValue(...)
        before = source[pos - 1] if pos > 0 else ""
        if before.isalnum() or before == "_":
            out.append(source[i:pos + len(method_name)])
            i = pos + len(method_name)
            continue

        open_idx = pos + len(method_name)
        end = find_matching_paren(source, open_idx)
        if end is None:
            out.append(source[i:])
            break

        out.append(source[i:pos])
        full_call = source[pos:end + 1]
        args_src = source[open_idx + 1:end]
        args = split_top_level_args(args_src)

        if len(args) == 2 and args[0] in {'"from"', '"to"'}:
            value_expr = args[1] if "toSqlTimestamp(" in args[1] else f"toSqlTimestamp({args[1]})"
            rewritten = f'{method_name}({args[0]}, {value_expr}, Types.TIMESTAMP_WITH_TIMEZONE)'
            out.append(rewritten)
            changed += 1
        else:
            out.append(full_call)
        i = end + 1

    return "".join(out), changed

# MapSqlParameterSource.addValue("from", instant) / params.addValue("to", instant)
text, add_value_rewrites = rewrite_calls(text, "addValue")


def rewrite_map_sql_parameter_source_constructors(source: str) -> tuple[str, int]:
    out = []
    i = 0
    changed = 0
    n = len(source)
    needle = "new MapSqlParameterSource("

    while i < n:
        pos = source.find(needle, i)
        if pos == -1:
            out.append(source[i:])
            break

        open_idx = pos + len("new MapSqlParameterSource")
        end = find_matching_paren(source, open_idx)
        if end is None:
            out.append(source[i:])
            break

        out.append(source[i:pos])
        args_src = source[open_idx + 1:end]
        args = split_top_level_args(args_src)

        if len(args) == 2 and args[0] in {'"from"', '"to"'} and "toSqlTimestamp(" not in args[1]:
            rewritten = (
                f'new MapSqlParameterSource()'
                f'.addValue({args[0]}, toSqlTimestamp({args[1]}), Types.TIMESTAMP_WITH_TIMEZONE)'
            )
            out.append(rewritten)
            changed += 1
        else:
            out.append(source[pos:end + 1])
        i = end + 1

    return "".join(out), changed

text, ctor_rewrites = rewrite_map_sql_parameter_source_constructors(text)

# Handle partially applied previous patches that may have produced addValue("from", toSqlTimestamp(x))
# without the explicit SQL type.
text, timestamp_add_value_rewrites = rewrite_calls(text, "addValue")

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
    print("Patch already applied or no matching from/to Instant parameter binding was found.")
else:
    path.write_text(text)
    total = add_value_rewrites + ctor_rewrites + timestamp_add_value_rewrites
    print(
        f"Updated {path}: {total} from/to binding(s) now use explicit TIMESTAMP_WITH_TIMEZONE typing "
        f"({add_value_rewrites} addValue, {ctor_rewrites} constructor, {timestamp_add_value_rewrites} second-pass)."
    )
PY

echo "Patch applied: character online activity from/to SQL parameters now bind with explicit PostgreSQL timestamp typing."
echo "Run: make test"
echo "Then: make test-coverage"
