#!/usr/bin/env bash
set -euo pipefail

TEST_FILE="src/test/java/com/nathan/tibiastats/highscore/HighscoreRateLimitCircuitBreakerIntegrationTest.java"
DEV_YML="src/main/resources/application-dev.yml"

if [[ ! -f "$TEST_FILE" || ! -f "$DEV_YML" ]]; then
  echo "ERROR: expected TibiaChrono project files were not found." >&2
  echo "Run this script from the TibiaChrono project root." >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path
import re

# 1) Make the rate-limit integration test independent from previous test runs.
# The test database container uses tmpfs and may stay alive between invocations of
# ./run-tests.sh. If a previous run activated global highscore HTTP backoff, the
# next run can skip the mocked fetch entirely unless the singleton table is reset.
test_path = Path("src/test/java/com/nathan/tibiastats/highscore/HighscoreRateLimitCircuitBreakerIntegrationTest.java")
text = test_path.read_text()

if "highscore_http_backoff_state," not in text:
    needle = """        jdbc.execute(\"\"\"\n            truncate table\n                highscore_exp_rank_daily,"""
    replacement = """        jdbc.execute(\"\"\"\n            truncate table\n                highscore_http_backoff_state,\n                highscore_exp_rank_daily,"""
    if needle not in text:
        raise SystemExit("Could not safely update HighscoreRateLimitCircuitBreakerIntegrationTest cleanup block.")
    text = text.replace(needle, replacement, 1)
    test_path.write_text(text)
    print("Updated HighscoreRateLimitCircuitBreakerIntegrationTest cleanup to reset highscore_http_backoff_state.")
else:
    print("HighscoreRateLimitCircuitBreakerIntegrationTest cleanup already resets highscore_http_backoff_state.")

# 2) Keep the disabled manual highscore backfill plan conservative.
# The existing configuration test intentionally asserts this template plan uses a
# single scope worker and a single request worker when someone enables it manually.
yml_path = Path("src/main/resources/application-dev.yml")
yml = yml_path.read_text()

pattern = re.compile(
    r"(manual-backfill-all-highscores:\n(?:(?!\n\s{8}[a-zA-Z0-9_-]+:).)*?\n\s+parallelism:)\s*4(\n)",
    re.S,
)
yml2, count = pattern.subn(r"\g<1> 1\2", yml, count=1)
if count == 0:
    # Accept already-fixed config, otherwise fail loudly instead of changing the wrong plan.
    already_fixed = re.search(
        r"manual-backfill-all-highscores:\n(?:(?!\n\s{8}[a-zA-Z0-9_-]+:).)*?\n\s+parallelism:\s*1\n",
        yml,
        re.S,
    )
    if already_fixed:
        print("manual-backfill-all-highscores parallelism already set to 1.")
    else:
        raise SystemExit("Could not safely set manual-backfill-all-highscores parallelism to 1.")
else:
    yml_path.write_text(yml2)
    print("Updated manual-backfill-all-highscores parallelism to 1.")
PY

echo "Done. Run: ./run-tests.sh"
