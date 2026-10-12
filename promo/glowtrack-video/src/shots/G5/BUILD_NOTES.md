# G5 BUILD_NOTES（SC08 收尾 3828–4070）

- **构图回扣**：左侧彩虹图标 = `TrackLine`（G3/parts）S 折线 S_PTS w≈200（222–418 中心 (320,360)）+ 2 枚 PlayerDot 节点 + 末端 `HaloRing`（fx）紫光环，与右侧「GlowTrack」Audiowide 110px 白 + 紫硬投影 `6px 6px 0 rgba(102,45,248,1)` @ (860,330) 同帧淡入，呼应 SC02 高光登场（其 LightSweep 三连 + 大字 GlitchIn 写法复用）。
- **扫光白名单②**：3836/3844/3852 三轮 `LightSweep`（16f×3 错峰 8f，dy=0 对准大字行），全镜头仅此一处扫光。
- **大字 glitch**：3907 起公共 `GlitchIn rgbSplit=6 seed=5`（全镜头唯一 glitch），同步 `emphasisPulse 1.11`（transformOrigin 860,330）。3907 前用 plain 淡入常显（softOp 首帧即亮，避免空场判据空帧），GlitchIn 接管后无跳变。
- **副标**：3920 起「让每一个脚印都发光」CText 34px GREY letterSpacing 6 `SoftIn`。
- **S30 微缩路**：3938 起 5 条竖向轻波浪灰路（x=260/460/660/860/1060，y150–520，幅度/相位 `rnd(9,i,·)` 区分，GREY_LINE 2.5px α.55）+ 各一条彩虹细线 `TrackLine sw=2`，1 帧错峰 draw-on 20f。
- **收尾件**：3966 白 Pill「Modrinth · GlowTrack」(640,560) 320×52 slideUp + 3980 灰小字 22px SoftIn。
- **不主动离场**：无 exitOp/位移，保持满态到 4070（opacity 不归零），4070 后由 G0 片尾层覆盖淡出；紫光环呼吸（±4.5% sin）为持续动作。
- 注册：`SHOTS_G5 = [{id:'G5-SC08', from:3828, to:4070, Comp:SC08}]`（src/shots/G5/index.ts）。tsc --noEmit 通过；stills/G5 5 帧自检通过。
