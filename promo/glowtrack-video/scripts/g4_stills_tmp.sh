#!/bin/bash
# G4 still 临时驱动（绕开 PowerShell→bash 引号剥离问题）
set -e
cd /d/player-tracker/promo/glowtrack-video
export REMOTION_CHROME_EXECUTABLE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
rm -rf build_dev_g4
bash scripts/still_bash.sh G4 "2850,3000,3150,3350,3550,3700" "D:/player-tracker/promo/glowtrack-video/stills/G4" g4
