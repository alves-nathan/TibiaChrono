#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(pwd)"
TEST_FILE="src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java"

if [ ! -f "pom.xml" ] || [ ! -d "src/main/java/com/nathan/tibiastats" ]; then
  echo "ERROR: this script must be run from the TibiaChrono project root." >&2
  exit 1
fi

if [ ! -f "$TEST_FILE" ]; then
  echo "ERROR: $TEST_FILE was not found." >&2
  echo "This patch expects the architecture test introduced by the recent architecture/quality changes." >&2
  exit 1
fi

cp "$TEST_FILE" "$TEST_FILE.bak-arch-port-nested-contracts"

python3 <<'PY'
from pathlib import Path
import re

path = Path("src/test/java/com/nathan/tibiastats/architecture/ArchitectureRulesTest.java")
text = path.read_text()
original = text

# ArchUnit imports needed by the explicit predicate below.
if "import com.tngtech.archunit.base.DescribedPredicate;" not in text:
    text = text.replace(
        "package com.nathan.tibiastats.architecture;\n\n",
        "package com.nathan.tibiastats.architecture;\n\n"
        "import com.tngtech.archunit.base.DescribedPredicate;\n"
        "import com.tngtech.archunit.core.domain.JavaClass;\n",
        1,
    )
elif "import com.tngtech.archunit.core.domain.JavaClass;" not in text:
    text = text.replace(
        "import com.tngtech.archunit.base.DescribedPredicate;\n",
        "import com.tngtech.archunit.base.DescribedPredicate;\n"
        "import com.tngtech.archunit.core.domain.JavaClass;\n",
        1,
    )

predicate = '''
    private static final DescribedPredicate<JavaClass> TOP_LEVEL_CLASS =
            new DescribedPredicate<>("top-level classes") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return !javaClass.getName().contains("$");
                }
            };

'''

if "TOP_LEVEL_CLASS" not in text:
    # Insert as the first member of ArchitectureRulesTest.
    text, inserted = re.subn(
        r"((?:public\s+)?(?:final\s+)?class\s+ArchitectureRulesTest\s*\{\s*)",
        r"\1" + predicate,
        text,
        count=1,
    )
    if inserted == 0:
        raise SystemExit("ERROR: could not find the ArchitectureRulesTest class declaration.")

# Restrict the domain port rule to top-level classes only. Nested records/classes
# declared inside a port interface are part of the port contract, not standalone ports.
if ".and(TOP_LEVEL_CLASS)" not in text:
    text, count = re.subn(
        r'(\.resideInAPackage\("\.\.domain\.port\.\."\))',
        r'\1\n                .and(TOP_LEVEL_CLASS)',
        text,
        count=1,
    )
    if count == 0:
        raise SystemExit(
            "ERROR: could not find the domain port architecture rule using resideInAPackage(\"..domain.port..\")."
        )

if text == original:
    print("Architecture rule already looked patched; no changes needed.")
else:
    path.write_text(text)
    print(f"Updated {path}")
PY

echo "Patch applied. Run: make test"
