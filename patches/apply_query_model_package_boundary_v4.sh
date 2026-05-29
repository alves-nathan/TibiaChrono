#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="query-model-package-boundary-v4"
BACKUP_DIR="patches/.backups/${PATCH_NAME}-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
backup_if_exists() {
  local file="$1"
  if [[ -f "$file" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

query_classes=(
  "ApiQueryService"
  "CharacterTimelineService"
  "GuildQueryService"
  "HighscoreApiQueryService"
  "WorldOnlineAnalyticsService"
)

for class_name in "${query_classes[@]}"; do
  backup_if_exists "src/main/java/com/nathan/tibiastats/application/service/${class_name}.java"
  backup_if_exists "src/main/java/com/nathan/tibiastats/application/query/${class_name}.java"
done
backup_if_exists "src/main/java/com/nathan/tibiastats/application/query/ReadModelComponent.java"
backup_if_exists "src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java"

python3 - <<'PY'
from pathlib import Path
import re

root = Path('.')
service_pkg = root / 'src/main/java/com/nathan/tibiastats/application/service'
query_pkg = root / 'src/main/java/com/nathan/tibiastats/application/query'
query_pkg.mkdir(parents=True, exist_ok=True)

query_classes = [
    'ApiQueryService',
    'CharacterTimelineService',
    'GuildQueryService',
    'HighscoreApiQueryService',
    'WorldOnlineAnalyticsService',
]

annotation = query_pkg / 'ReadModelComponent.java'
annotation.write_text('''package com.nathan.tibiastats.application.query;

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
''')

type_decl_re = re.compile(
    r"(?m)^([ \t]*)(public\s+)?((?:abstract\s+|final\s+|sealed\s+|non-sealed\s+)*)"
    r"(class|interface|record|enum|@interface)\s+([A-Za-z_][A-Za-z0-9_]*)"
)

def mark_read_model(text: str) -> str:
    text = text.replace('package com.nathan.tibiastats.application.service;', 'package com.nathan.tibiastats.application.query;')
    text = re.sub(r"(?m)^import\s+org\.springframework\.stereotype\.Service;\s*\n", "", text)
    text = re.sub(r"(?m)^([ \t]*)@Service\s*$", r"\1@ReadModelComponent", text, count=1)
    if '@ReadModelComponent' not in text:
        match = type_decl_re.search(text)
        if not match:
            raise SystemExit('Could not locate a supported top-level type declaration while marking a read model')
        text = text[:match.start()] + f"{match.group(1)}@ReadModelComponent\n" + text[match.start():]
    return text

for class_name in query_classes:
    src = service_pkg / f'{class_name}.java'
    dst = query_pkg / f'{class_name}.java'
    if src.exists():
        text = mark_read_model(src.read_text())
        dst.write_text(text)
        src.unlink()
    elif dst.exists():
        dst.write_text(mark_read_model(dst.read_text()))
    else:
        raise SystemExit(f'Could not find {class_name}.java in service or query package')

# Update imports across production and tests.
for path in list((root / 'src/main/java').rglob('*.java')) + list((root / 'src/test/java').rglob('*.java')):
    text = path.read_text()
    original = text
    for class_name in query_classes:
        text = text.replace(
            f'import com.nathan.tibiastats.application.service.{class_name};',
            f'import com.nathan.tibiastats.application.query.{class_name};'
        )
    if text != original:
        path.write_text(text)

arch = root / 'src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java'
if not arch.exists():
    raise SystemExit('ArchitectureRulesTest.java not found; apply quality gates patch first')

text = arch.read_text()

def ensure_import(text: str, imp: str) -> str:
    if imp in text:
        return text
    return re.sub(r'(?m)^(package\s+[^;]+;\s*\n)', r'\1\n' + imp + '\n', text, count=1)

for imp in [
    'import com.nathan.tibiastats.application.query.ReadModelComponent;',
    'import com.tngtech.archunit.base.DescribedPredicate;',
    'import com.tngtech.archunit.core.domain.JavaClass;',
]:
    text = ensure_import(text, imp)

if 'NOT_READ_MODEL_MARKER' not in text:
    predicate = '''
    private static final DescribedPredicate<JavaClass> NOT_READ_MODEL_MARKER =
            new DescribedPredicate<>("not the ReadModelService marker") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return !javaClass.getSimpleName().equals("ReadModelService");
                }
            };
'''
    insert_at = text.find('    @ArchTest')
    if insert_at == -1:
        insert_at = text.rfind('\n}')
    if insert_at == -1:
        raise SystemExit('Could not find insertion point in ArchitectureRulesTest.java')
    text = text[:insert_at] + predicate + '\n' + text[insert_at:]

query_rule = '''
    @ArchTest
    static final ArchRule query_services_should_be_marked_as_read_models = classes()
            .that().resideInAPackage("..application.query..")
            .and().haveSimpleNameEndingWith("Service")
            .and(NOT_READ_MODEL_MARKER)
            .should().beAnnotatedWith(ReadModelComponent.class);
'''
if 'query_services_should_be_marked_as_read_models' not in text:
    insert_at = text.rfind('\n}')
    if insert_at == -1:
        raise SystemExit('Could not find closing brace in ArchitectureRulesTest.java')
    text = text[:insert_at] + query_rule + text[insert_at:]
else:
    text = re.sub(
        r'(?s)    @ArchTest\s+static final ArchRule query_services_should_be_marked_as_read_models = classes\(\).*?\.should\(\)\.beAnnotatedWith\(ReadModelComponent\.class\);',
        query_rule.strip('\n'),
        text,
        count=1,
    )

jdbc_rule = '''
    @ArchTest
    static final ArchRule write_side_application_services_should_not_use_spring_jdbc_directly = noClasses()
            .that().resideInAPackage("..application.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc.."
            );
'''
if 'write_side_application_services_should_not_use_spring_jdbc_directly' not in text:
    insert_at = text.rfind('\n}')
    text = text[:insert_at] + jdbc_rule + text[insert_at:]

text = text.replace('\n    \n    private static final DescribedPredicate', '\n\n    private static final DescribedPredicate')
text = text.replace('\n@ArchTest\n', '\n    @ArchTest\n')
arch.write_text(text)
PY

find src/main/java src/test/java -name '*.java' -type f -exec chmod 0644 {} +
chmod 0755 "$0" 2>/dev/null || true

cat <<MSG
Done. Query model package boundary applied.
Backup directory: $BACKUP_DIR
Next step: make qa
MSG
