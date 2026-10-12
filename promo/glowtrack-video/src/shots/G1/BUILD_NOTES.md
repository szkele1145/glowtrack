# G1 BUILD_NOTES（SC01 处境 78–586 + SC02 登场高光 632–1092）

- 镜头表：SC01（三段小剧场：机架+举报气泡→双人跑动+?→日志卡+Cross）/ SC02（GlowTrack 大字 GlitchIn 登场 + LightSweep 三连 + 眼睛+足迹+彩虹曲线 + 采样演示：机架+小人四步撒光点）。
- 图元：src/shots/G1/parts.tsx（TrackLine/ServerIcon/PlayerDot/Runner/HouseIcon/Bubble/LogSheet/Road/ClockBadge/EyeIcon）。
- glitch 白名单：SC01「?」、SC02「GlowTrack」+三 Pill。扫光白名单①：SC02。
- 主角：GlowTrack 大字 132px 紫硬投影（SC02）；彩虹曲线+光点带辉光。配角不发光。
- 自检：stills/G1/ 28 张（OCR 核过「白名单里的玩家一动」「它给服务器装上一双眼睛」字幕对位）；亮像素动态确认无空帧。
- 交付：index.ts SHOTS_G1 已填（78–586 / 632–1092）；agent 上下文耗尽前完成全部两镜头，主会话验收 tsc 0 错。