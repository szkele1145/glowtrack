# G2 BUILD_NOTES（SC03 热力 1113–1655 + SC04 穿墙 1676–2025）

- SC03：推送亮起（机架+光点卡+「攒批」SoftIn+弧箭头+MonitorIcon 屏内彩虹线 30f draw-on+幕底压暗）→ 演示区接管（显示器缩 0.5 移角）；左半 #58A6FF 纯色、右半绿→红烧 draw-on；三速度位 Orbitron 26px + PlayerDot 呼吸；色带图例 800×14；「快」唯一 GlitchIn；停 48 帧。
- SC04：三山 2f 错峰 slideUp；彩虹线整条 30f draw-on + 辉光层宽36 α0.18；中段虚线穿墙 +「隔墙可见」Pill（唯一 GlitchIn）；亮度波 kf 24f 持续循环；线宽 12→14→12 起伏；停 44 帧。
- 图元：MonitorIcon/DotCard/ArcArrow/MountainRange 自建（G2/parts.tsx）；TrackLine/ServerIcon/PlayerDot 复用 G3/parts。
- 自检：stills/G2/ 7 张（1150/1250/1400/1550/1700/1900/2000）抽查无空帧遮挡；修复过弧箭头越界、亮度波循环两处。tsc 0 错。