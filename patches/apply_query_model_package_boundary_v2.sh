#!/usr/bin/env bash
set -Eeuo pipefail

if [[ ! -f "pom.xml" || ! -d "src/main/java/com/nathan/tibiastats" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

STAMP="$(date +%Y%m%d%H%M%S)"
PATCH_NAME="query-model-package-boundary-v2"
BACKUP_DIR="patches/.backups/${PATCH_NAME}-${STAMP}"
mkdir -p "$BACKUP_DIR"

backup_if_exists() {
  local f="$1"
  if [[ -f "$f" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$f")"
    cp "$f" "$BACKUP_DIR/$f"
  fi
}

QUERY_CLASSES=(
  ApiQueryService
  HighscoreApiQueryService
  CharacterTimelineService
  WorldOnlineAnalyticsService
  GuildQueryService
)

for cls in "${QUERY_CLASSES[@]}"; do
  backup_if_exists "src/main/java/com/nathan/tibiastats/application/service/${cls}.java"
  backup_if_exists "src/main/java/com/nathan/tibiastats/application/query/${cls}.java"
done

backup_if_exists "src/main/java/com/nathan/tibiastats/application/query/ReadModelService.java"

while IFS= read -r -d '' f; do
  backup_if_exists "$f"
done < <(find src/main/java src/test/java -type f -name '*.java' -print0)

mkdir -p src/main/java/com/nathan/tibiastats/application/query

cat > src/main/java/com/nathan/tibiastats/application/query/ReadModelService.java <<'JAVA'
package com.nathan.tibiastats.application.query;

import org.springframework.stereotype.Service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Service
public @interface ReadModelService {
}
JAVA

python3 <<'PY'
from pathlib import Path

root = Path('.')
query_classes = [
    'ApiQueryService',
    'HighscoreApiQueryService',
    'CharacterTimelineService',
    'WorldOnlineAnalyticsService',
    'GuildQueryService',
]

service_dir = root / 'src/main/java/com/nathan/tibiastats/application/service'
query_dir = root / 'src/main/java/com/nathan/tibiastats/application/query'
query_dir.mkdir(parents=True, exist_ok=True)

for cls in query_classes:
    src = service_dir / f'{cls}.java'
    dst = query_dir / f'{cls}.java'

    if src.exists():
        text = src.read_text()
    elif dst.exists():
        text = dst.read_text()
    else:
        raise SystemExit(f'ERROR: neither {src} nor {dst} exists; cannot move {cls}.')

    text = text.replace('package com.nathan.tibiastats.application.service;',
                        'package com.nathan.tibiastats.application.query;')
    text = text.replace('import org.springframework.stereotype.Service;\n', '')
    text = text.replace('@Service\npublic class ', '@ReadModelService\npublic class ')
    text = text.replace('@ReadModelService\n@ReadModelService\npublic class ', '@ReadModelService\npublic class ')

    dst.write_text(text)
    if src.exists():
        src.unlink()

# Update imports in all Java files.
java_roots = [root / 'src/main/java', root / 'src/test/java']
for java_root in java_roots:
    if not java_root.exists():
        continue
    for path in java_root.rglob('*.java'):
        text = path.read_text()
        original = text
        for cls in query_classes:
            text = text.replace(
                f'import com.nathan.tibiastats.application.service.{cls};',
                f'import com.nathan.tibiastats.application.query.{cls};'
            )
            # Keep fully-qualified usage consistent if any exists.
            text = text.replace(
                f'com.nathan.tibiastats.application.service.{cls}',
                f'com.nathan.tibiastats.application.query.{cls}'
            )
        if text != original:
            path.write_text(text)

arch = root / 'src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java'
if arch.exists():
    text = arch.read_text()

    if 'import com.nathan.tibiastats.application.query.ReadModelService;' not in text:
        package_line = 'package com.nathan.tibiastats.architecture;\n\n'
        if package_line in text:
            text = text.replace(package_line, package_line + 'import com.nathan.tibiastats.application.query.ReadModelService;\n', 1)
        else:
            text = 'import com.nathan.tibiastats.application.query.ReadModelService;\n' + text

    read_rule_marker = 'read_model_services_should_live_in_query_package'
    jdbc_rule_marker = 'write_application_services_should_not_depend_on_spring_jdbc'
    rules_to_add = []

    if read_rule_marker not in text:
        rules_to_add.append('''

    @ArchTest
    static final ArchRule read_model_services_should_live_in_query_package = classes()
            .that().areAnnotatedWith(ReadModelService.class)
            .should().resideInAPackage("..application.query..");''')

    if jdbc_rule_marker not in text:
        rules_to_add.append('''

    @ArchTest
    static final ArchRule write_application_services_should_not_depend_on_spring_jdbc = noClasses()
            .that().resideInAPackage("..application.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..",
                    "org.springframework.jdbc.core..",
                    "org.springframework.jdbc.core.namedparam.."
            );''')

    if rules_to_add:
        stripped = text.rstrip()
        if not stripped.endswith('}'):
            raise SystemExit(f'ERROR: {arch} does not end with a closing brace.')
        text = stripped[:-1].rstrip() + ''.join(rules_to_add) + '\n}\n'

    arch.write_text(text)
else:
    raise SystemExit(f'ERROR: {arch} was not found; quality gate test must exist before applying this patch.')

# Normalize accidental executable bit warnings for Java files.
for java_root in java_roots:
    if not java_root.exists():
        continue
    for path in java_root.rglob('*.java'):
        try:
            path.chmod(0o644)
        except PermissionError:
            pass
PY

# Safety checks that do not require Maven.
for cls in "${QUERY_CLASSES[@]}"; do
  if [[ ! -f "src/main/java/com/nathan/tibiastats/application/query/${cls}.java" ]]; then
    echo "ERROR: ${cls} was not created under application/query." >&2
    exit 1
  fi
  if [[ -f "src/main/java/com/nathan/tibiastats/application/service/${cls}.java" ]]; then
    echo "ERROR: ${cls} still exists under application/service." >&2
    exit 1
  fi
  if ! grep -q "package com.nathan.tibiastats.application.query;" "src/main/java/com/nathan/tibiastats/application/query/${cls}.java"; then
    echo "ERROR: ${cls} has the wrong package declaration." >&2
    exit 1
  fi
  if ! grep -q "@ReadModelService" "src/main/java/com/nathan/tibiastats/application/query/${cls}.java"; then
    echo "ERROR: ${cls} was not annotated with @ReadModelService." >&2
    exit 1
  fi
done

if grep -R "import com\.nathan\.tibiastats\.application\.service\.\(ApiQueryService\|HighscoreApiQueryService\|CharacterTimelineService\|WorldOnlineAnalyticsService\|GuildQueryService\);" -n src/main/java src/test/java >/tmp/query-boundary-stale-imports.$$ 2>/dev/null; then
  echo "ERROR: stale service-package query imports remain:" >&2
  cat /tmp/query-boundary-stale-imports.$$ >&2
  rm -f /tmp/query-boundary-stale-imports.$$
  exit 1
fi
rm -f /tmp/query-boundary-stale-imports.$$

if ! grep -q "read_model_services_should_live_in_query_package" src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java; then
  echo "ERROR: Read model ArchUnit rule was not added." >&2
  exit 1
fi

if ! grep -q "write_application_services_should_not_depend_on_spring_jdbc" src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java; then
  echo "ERROR: JDBC boundary ArchUnit rule was not added." >&2
  exit 1
fi

echo "Done. Read model package boundary applied with a resilient v2 patch."
echo "Backup directory: $BACKUP_DIR"
echo "Next step: make qa"
