#!/bin/bash
# preview with Edge headless (chromium download blocked on this machine)
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd); cd "$ROOT"
export REMOTION_CHROME_EXECUTABLE="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
SEC=${1:-30}; FROM=${2:-0}
SLUG=$(python -c "import re;print(re.search(r\"slug:\\s*'([^']+)'\", open('src/config.ts', encoding='utf-8').read()).group(1))")
TOTAL=$(python -c "import re;print(re.search(r'TOTAL_FRAMES\s*=\s*(\d+)', open('src/common/timeline.ts', encoding='utf-8').read()).group(1))")
A=$((FROM * 30)); B=$((A + SEC * 30 - 1))
[ $B -gt $((TOTAL - 1)) ] && B=$((TOTAL - 1))
mkdir -p renders
OUTF="renders/${SLUG}_preview_${FROM}-$((FROM + SEC))s.mp4"
[ "${SKIP_BUNDLE:-0}" = 1 ] && [ -d build_prev ] || { rm -rf build_prev && npx remotion bundle src/index.ts --out-dir build_prev --log=error; }
npx remotion render build_prev Video "$OUTF" --codec=h264 --crf=18 --frames=$A-$B --concurrency=${CONC:-6} --timeout=${RTIMEOUT:-600000} --log=error
[ -s "$OUTF" ] || { echo "PREVIEW FAILED"; exit 1; }
[ "${KEEP_BUNDLE:-0}" = 1 ] || rm -rf build_prev
echo "$OUTF"
