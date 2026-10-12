# BUILD_NOTES — G2 组（SC03 / SC04）

## 交付
- `src/shots/G2/parts.tsx`：组内图元 — MonitorIcon（白边 260×180 + 支柱/底座，children 可塞屏幕内容）、DotCard（光点卡）、ArcArrow（服务器→显示器弧形箭头 draw-on）、MountainRange（三角简笔山脉，逐峰 dy/op 供错峰）。TrackLine / ServerIcon / PlayerDot 直接复用 `G3/parts.tsx`（分镜表描述几何一致，暂不切 G1）。
- `src/shots/G2/SC03.tsx`（1113–1655）：前半 ServerIcon+三张光点卡叠放+「攒批」Pill（SoftIn）→ 弧形箭头 24 帧 draw-on → 右下 MonitorIcon → 屏内 TrackLine 30 帧彩虹 draw-on（clipPath 裁在屏内）→ 1362 起幕底压暗 rgba(0,0,0,.35)×12 帧；后半 1390 显示器 kf 缩 0.5 移 (160,170)，横向轨迹 x200–1080 y440 宽 12：左半 #58A6FF 纯色 115 帧长出（带二次缓出），右半 1523 起 74 帧长出、颜色 mixHex(绿→红) 烧 130 帧（色相 0.33→0 等效），速度位标 0.5/2/5 格/s（Orbitron 26px，1 帧错峰）+ PlayerDot r13 α≈0.5 呼吸，色带图例 800×14 scaleX wipe + 慢/快 CText（「快」=本镜头唯一 GlitchIn）。落位 1597 停 48 帧，1645 起 1−(n/10)^1.5 归零。
- `src/shots/G2/SC04.tsx`（1676–2025）：三山 2 帧错峰 slideUp Δ60；彩虹轨迹 x80–1200 y380 宽 12 整条 30 帧 draw-on（辉光层宽 36 α0.18 同形 dashoffset 同步长出）；中段 500–800 α0.55 白虚线示意穿墙；「隔墙可见」紫 Pill 挂 y300（=本镜头唯一 GlitchIn，下挂虚线引线 SoftIn）；「线宽 5 格」CText 28px (640,240)；亮度波=白光点 r6+外圈 70% 光晕沿线左→右，kf 周期函数 24 帧循环（持续到离场灭光），线宽 kf 12→14→12（周期 40 帧）；落位停 44 帧，2015 起 exitOp 8 帧 + glowOffK 先灭光。
- `src/shots/G2/index.ts`：SHOTS_G2 登记 G2-SC03 / G2-SC04；BG_G2 / FOOTAGE_G2 仍为空（幕底压暗用叠层实现，未写 BG_G2）。

## 约定遵守
- N = useCurrentFrame() + F0；随机只用 rnd；glitch 每镜头仅一处（SC03「快」、SC04「隔墙可见」），其余文字 SoftIn/CText。
- 无不透明黑底、无字幕块；内容 y110–620；硬切前 opAll 归零（SC03 用任务指定的 1−(n/10)^1.5，SC04 用共用 exitOp）。
- 已知取舍：右半烧红用 mixHex 端色插值近似 hue 0.33→0（heatColor 分段端色一致）；亮度波循环与线宽起伏共用 glowOffK 提前灭光，避免硬切带光。
