#!/bin/bash
# still with Edge headless (chromium download blocked on this machine)
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd); cd "$ROOT"
export REMOTION_CHROME_EXECUTABLE="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
COMP=$1; FRAMES=$2; OUT=$3; TAG=${4:-$COMP}
B=build_dev_$TAG
[ -d "$B" ] || npx remotion bundle src/index.ts --out-dir "$B" --log=error
mkdir -p "$OUT"
IFS=',' read -ra FRAMES_ARR <<< "$FRAMES"
for N in "${FRAMES_ARR[@]}"; do
  npx remotion still "$B" "$COMP" "$OUT/f_$(printf %04d "$N").png" --frame=$((N-1)) --log=error
done
ls "$OUT" | wc -l
