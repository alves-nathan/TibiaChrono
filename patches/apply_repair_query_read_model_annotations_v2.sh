#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="repair-query-read-model-annotations-v2"
BACKUP_DIR="patches/.backups/${PATCH_NAME}-$(date +%Y%m%d%H%M%S)"
QUERY_DIR="src/main/java/com/nathan/tibiastats/application/query"
ANNOTATION_FILE="${QUERY_DIR}/ReadModelComponent.java"
ARCH_RULES_FILE="src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java"

if [[ ! -d "src/main/java/com/nathan/tibiastats" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

if [[ ! -d "$QUERY_DIR" ]]; then
  echo "ERROR: $QUERY_DIR does not exist. Apply the query model package boundary patch first." >&2
  exit 1
fi

if [[ ! -f "$ARCH_RULES_FILE" ]]; then
  echo "ERROR: $ARCH_RULES_FILE not found. Apply the quality gates patch first." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

backup_file() {
  local file="$1"
  if [[ -f "$file" ]]; then
    local dest="$BACKUP_DIR/$file"
    mkdir -p "$(dirname "$dest")"
    cp "$file" "$dest"
  fi
}

backup_file "$ANNOTATION_FILE"
backup_file "$ARCH_RULES_FILE"
find "$QUERY_DIR" -maxdepth 1 -type f -name '*Service.java' -print0 | while IFS= read -r -d '' file; do
  backup_file "$file"
done

cat > "$ANNOTATION_FILE" <<'EOF'
package com.nathan.tibiastats.application.query;

import org.springframework.stereotype.Service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Service
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadModelComponent {
}
EOF

python3 - <<'PY'
from pathlib import Path
import re
import sys

query_dir = Path("src/main/java/com/nathan/tibiastats/application/query")
arch_file = Path("src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java")
service_files = sorted(query_dir.glob("*Service.java"))

if not service_files:
    raise SystemExit("ERROR: no *Service.java files found under application/query")

# Top-level Java type declaration supported by this repair. This intentionally handles
# classes, interfaces, records, enums and annotation declarations (@interface).
type_decl_re = re.compile(
    r"(?m)^([ \t]*)(public\s+)?((?:abstract\s+|final\s+|sealed\s+|non-sealed\s+)*)"
    r"(class|interface|record|enum|@interface)\s+([A-Za-z_][A-Za-z0-9_]*)"
)

changed_files = []
checked_files = []
for path in service_files:
    text = path.read_text()
    original = text
    checked_files.append(str(path))

    # Query read models should use the semantic stereotype, not the generic Spring one.
    text = re.sub(r"(?m)^import\s+org\.springframework\.stereotype\.Service;\s*\n", "", text)

    # If @Service is already on the type, replace it. This is safe for class/interface/record/enum.
    text = re.sub(r"(?m)^([ \t]*)@Service\s*$", r"\1@ReadModelComponent", text, count=1)

    if not re.search(r"(?m)^\s*@ReadModelComponent\s*$", text):
        match = type_decl_re.search(text)
        if not match:
            # Do not leave the repository half-patched because of an unexpected marker file.
            # The ArchUnit rule is also repaired below to ignore the marker interface if present.
            print(f"WARN: could not locate a supported top-level type declaration in {path}; leaving it unchanged.", file=sys.stderr)
        else:
            indent = match.group(1)
            insert_at = match.start()
            text = text[:insert_at] + f"{indent}@ReadModelComponent\n" + text[insert_at:]

    if text != original:
        path.write_text(text)
        changed_files.append(str(path))

# Repair the ArchUnit rule so it does not classify the optional ReadModelService marker
# itself as a read model implementation. This keeps the rule focused on concrete query services.
arch_text = arch_file.read_text()
arch_original = arch_text

required_imports = [
    "import com.nathan.tibiastats.application.query.ReadModelComponent;",
    "import com.tngtech.archunit.base.DescribedPredicate;",
    "import com.tngtech.archunit.core.domain.JavaClass;",
]
for imp in required_imports:
    if imp not in arch_text:
        arch_text = re.sub(r"(?m)^(package\s+[^;]+;\s*\n)", r"\1\n" + imp + "\n", arch_text, count=1)

if "NOT_READ_MODEL_MARKER" not in arch_text:
    predicate = '''
    private static final DescribedPredicate<JavaClass> NOT_READ_MODEL_MARKER =
            new DescribedPredicate<>("not the ReadModelService marker") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return !javaClass.getSimpleName().equals("ReadModelService");
                }
            };
'''
    marker = "    @ArchTest\n    static final ArchRule query_services_should_be_marked_as_read_models"
    if marker in arch_text:
        arch_text = arch_text.replace(marker, predicate + "\n" + marker, 1)
    else:
        # Fallback: insert before the final class closing brace.
        idx = arch_text.rfind("}")
        if idx != -1:
            arch_text = arch_text[:idx] + predicate + "\n" + arch_text[idx:]

# Insert the predicate into the query read-model rule if it is not already there.
arch_text = re.sub(
    r'(static\s+final\s+ArchRule\s+query_services_should_be_marked_as_read_models\s*=\s*classes\(\)\s*\n'
    r'\s*\.that\(\)\.resideInAPackage\("\.\.application\.query\.\."\)\s*\n'
    r'\s*\.and\(\)\.haveSimpleNameEndingWith\("Service"\)\s*\n)'
    r'(\s*\.should\(\)\.beAnnotatedWith\(ReadModelComponent\.class\);)',
    r'\1            .and(NOT_READ_MODEL_MARKER)\n\2',
    arch_text,
    count=1,
)

if arch_text != arch_original:
    arch_file.write_text(arch_text)
    changed_files.append(str(arch_file))

print("Read-model service files checked:")
for path in checked_files:
    print(f" - {path}")
print("Changed files:")
if changed_files:
    for path in changed_files:
        print(f" - {path}")
else:
    print(" - none; files were already consistent")
PY

# Normalize permissions: Java sources should not be executable.
find src/main/java src/test/java -type f -name '*.java' -exec chmod 0644 {} +
chmod 0755 "$0" 2>/dev/null || true

echo "Done. Query read-model annotations repaired."
echo "Backup directory: $BACKUP_DIR"
echo "Next step: make qa"
