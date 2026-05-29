#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="query-model-package-boundary-v3"
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

for file in \
  "src/main/java/com/nathan/tibiastats/application/service/ApiQueryService.java" \
  "src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java" \
  "src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java" \
  "src/main/java/com/nathan/tibiastats/application/service/HighscoreApiQueryService.java" \
  "src/main/java/com/nathan/tibiastats/application/service/WorldOnlineAnalyticsService.java" \
  "src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java"; do
  backup_if_exists "$file"
done

python3 - <<'PY'
from pathlib import Path
import re
import shutil

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

(query_pkg / 'ReadModelComponent.java').write_text('''package com.nathan.tibiastats.application.query;

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

for class_name in query_classes:
    src = service_pkg / f'{class_name}.java'
    dst = query_pkg / f'{class_name}.java'
    if src.exists():
        text = src.read_text()
        text = text.replace('package com.nathan.tibiastats.application.service;', 'package com.nathan.tibiastats.application.query;')
        text = text.replace('import org.springframework.stereotype.Service;\n', '')
        text = re.sub(r'(?m)^@Service\s*$', '@ReadModelComponent', text)
        dst.write_text(text)
        src.unlink()
    elif dst.exists():
        text = dst.read_text()
        text = text.replace('package com.nathan.tibiastats.application.service;', 'package com.nathan.tibiastats.application.query;')
        text = text.replace('import org.springframework.stereotype.Service;\n', '')
        text = re.sub(r'(?m)^@Service\s*$', '@ReadModelComponent', text)
        dst.write_text(text)
    else:
        raise SystemExit(f'Could not find {class_name}.java in service or query package')

# Update imports across production and test sources.
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
if arch.exists():
    text = arch.read_text()
    if 'import com.nathan.tibiastats.application.query.ReadModelComponent;' not in text:
        text = text.replace(
            'package com.nathan.tibiastats.architecture;\n\n',
            'package com.nathan.tibiastats.architecture;\n\nimport com.nathan.tibiastats.application.query.ReadModelComponent;\n'
        )

    additions = '''

    @ArchTest
    static final ArchRule query_services_should_be_marked_as_read_models = classes()
            .that().resideInAPackage("..application.query..")
            .and().haveSimpleNameEndingWith("Service")
            .should().beAnnotatedWith(ReadModelComponent.class);

    @ArchTest
    static final ArchRule write_side_application_services_should_not_use_spring_jdbc_directly = noClasses()
            .that().resideInAPackage("..application.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc.."
            );
'''
    if 'query_services_should_be_marked_as_read_models' not in text:
        insert_at = text.rfind('\n}')
        if insert_at == -1:
            raise SystemExit('Could not find closing brace in ArchitectureRulesTest.java')
        text = text[:insert_at] + additions + text[insert_at:]

    # Clean up a cosmetic issue left by earlier patches in some bases.
    text = text.replace('\n    \n    private static final DescribedPredicate', '\n\n    private static final DescribedPredicate')
    text = text.replace('\n@ArchTest\n', '\n    @ArchTest\n')
    arch.write_text(text)
else:
    raise SystemExit('ArchitectureRulesTest.java not found; apply quality gates patch first')
PY

find src/main/java src/test/java -name '*.java' -type f -exec chmod 0644 {} +

cat <<MSG
Done. Query model package boundary applied.
Backup directory: $BACKUP_DIR
Next step: make qa
MSG
