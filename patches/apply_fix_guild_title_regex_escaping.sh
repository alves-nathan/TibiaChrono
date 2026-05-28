#!/usr/bin/env bash
set -euo pipefail

FILE="src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java"

if [[ ! -f "$FILE" ]]; then
  echo "ERROR: $FILE not found. Run this from the TibiaChrono project root." >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path

path = Path('src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java')
text = path.read_text()
original = text

# Fix Java string regex escapes accidentally written as \s instead of \\s.
# In Java source, regex whitespace must be represented as "\\s" inside string literals.
text = text.replace('"(?i)(^|\\s)" + Pattern.quote(normalizedValue) + "(?=\\s|$)"',
                    '"(?i)(^|\\\\s)" + Pattern.quote(normalizedValue) + "(?=\\\\s|$)"')

# Defensive fixes for any other regex literals that may have been accidentally reduced.
text = text.replace('"(?i)\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2}\\s+\\d{4}\\b"',
                    '"(?i)\\\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\\\s+\\\\d{1,2}\\\\s+\\\\d{4}\\\\b"')
text = text.replace('"(?i)\\b(online|offline)\\b"',
                    '"(?i)\\\\b(online|offline)\\\\b"')

if text == original:
    print('No escaping changes were needed; file may already be fixed.')
else:
    path.write_text(text)
    print('Fixed regex escaping in JsoupGuildAdapter.java.')
PY

echo "Done. Run: ./run-tests.sh"
