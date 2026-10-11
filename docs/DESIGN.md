# GlowTrack 玩家轨迹追踪模组 · 架构设计

- Minecraft **1.21.1** / NeoForge **21.1.x** / Java 21
- 模组 ID：`glowtrack`，包名：`com.glowtrack`
- 已确认需求（来自选品页）：渲染 5 项（速度热力、发光光晕、穿墙、时间刻度标签、脉冲流动虚线）、彩虹配色、线宽 5.0、双模式架构、文件持久化+导入导出、OP4+LuckPerms、Xaero API 画线、采样 GUI 内可调

---

## 1. 模块划分

```
com.glowtrack
├── GlowTrack.java                 // @Mod 主类，模组生命周期入口
├── common/
│   ├── config/TrackConfig.java    // 配置（采样/保留/权限/渲染默认值）
│   ├── track/
│   │   ├── TrackPoint.java        // 轨迹点（不可变）
│   │   ├── PlayerTrack.java       // 单玩家轨迹（点序列 + 元信息）
│   │   ├── TrackStore.java        // 内存索引 + 文件持久化 + 导入导出
│   │   ├── Sampler.java           // 采样策略（间隔/距离/拐弯补点）
│   │   └── RetentionPolicy.java   // 保留天数/容量上限清理
│   ├── network/                   // CustomPacketPayload 定义与编解码
│   └── command/TrackCommands.java // /gt 命令
├── server/
│   ├── TrackRecorder.java         // ServerTickEvent 采样 + 白名单枚举
│   └── ServerNetworkHandler.java  // 处理 C2S，下发 S2C
└── client/
    ├── ClientTrackCache.java      // 客户端轨迹缓存（网络或本地）
    ├── TrackRenderer.java         // RenderLevelStageEvent 世界内渲染
    ├── ClientEvents.java          // 客户端 tick、按键、降级采样
    ├── gui/TrackScreen.java       // 主 GUI（列表+搜索+控制面板）
    ├── gui/widget/                // 自定义控件（玩家条目、滑块）
    └── xaero/XaeroBridge.java     // Xaero 地图联动（可选依赖）
```

## 2. 双模式数据流

**模式 A：服务端记录（服务器装了模组）**
```
服务端 TickEvent → Sampler 判定 → TrackStore.append → 落盘(节流)
OP 客户端 C2S StartTracking(uuid)
服务端 → S2C TrackSnapshot(历史) + TrackStream(增量 tick 推送)
客户端 TrackRenderer 读 ClientTrackCache 实时渲染
```

**模式 B：客户端降级（单人 / 原版服务器）**
```
客户端检测：握手包未收到服务端 hello → 判定降级
ClientTickEvent → Sampler 判定本地已加载实体 → 本地 TrackStore → 直接渲染
```
判定依据：服务端装有模组时会主动发 `S2CHello`；客户端 N 秒内未收到则切 B 模式，并在 GUI 显示"本地观察模式"角标。

## 3. 数据模型与存储格式

`TrackPoint`：`x, y, z`（double）、`dimension`（ResourceKey）、`timeMillis`、`speed`（格/tick）、`yaw`、`pitch`

持久化：JSON，**按玩家 + 日期分文件**
- 服务端：`<world>/data/glowtrack/<uuid>/<yyyy-MM-dd>.json`
- 客户端降级：`.minecraft/glowtrack/<uuid>/<yyyy-MM-dd>.json`

```json
{
  "version": 1,
  "player": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "name": "Notch",
  "date": "2026-10-11",
  "points": [
    { "t": 1760160000000, "x": 12.5, "y": 64.0, "z": -8.3,
      "d": "minecraft:overworld", "v": 0.21, "yaw": 91.5, "pitch": 3.2 }
  ]
}
```
- 写入节流：内存缓冲，每 N 秒或退出时 flush
- 导出：`.json`（原生格式）与 `.csv`（`time,x,y,z,dim,speed`）
- 导入：读取 json/csv 合并进 TrackStore，重复点按 time 去重

## 4. 网络协议（CustomPacketPayload）

| 包 | 方向 | 载荷 |
|---|---|---|
| `hello` | S→C | protocolVersion（握手，兼作降级判定） |
| `track_list_req` | C→S | - |
| `track_list` | S→C | 白名单玩家列表（uuid, name, online） |
| `start_tracking` | C→S | uuid |
| `stop_tracking` | C→S | uuid |
| `track_snapshot` | S→C | uuid, points[]（进服时先推历史） |
| `track_stream` | S→C | uuid, points[]（增量，按 tick 批量） |
| `sync_settings` | C→S | 采样间隔/保留天数（OP 在 GUI 内调） |

所有 C→S 在服务端校验权限（OP4 或权限节点）。

## 5. 渲染管线（选品：热力+发光+穿墙+时间标签+虚线脉冲）

事件：`RenderLevelStageEvent`（`Stage.AFTER_ENTITIES`）
- 用 `Camera` 位置做相对偏移，避免远坐标浮点抖动
- 只画与观察者同维度的轨迹（跨维度点忽略，GUI 中提示）
- 线宽 5.0，按距离做近大远小缩放

**穿墙（关深度测试）**：自定义 `RenderType`，`depthTest = NO_DEPTH_TEST` + `writeMask = COLOR_WRITE`
**发光光晕**：`RenderType` 的 `Transparency` 用 additive（`BlendFactor.SRC_ALPHA, ONE`），画两遍（宽虚影 + 窄亮芯）
**彩虹热力**：每段按 `speed` 在 蓝→青→绿→黄→橙→红 色带上取色，顶点着色逐段变化
**脉冲流动虚线**：MC 线不支持 dash → 预处理成虚线段列表，用 `tickCount` 偏移 dashOffset 实现流动
**时间刻度标签**：轨迹上每 N 秒一个点，用 `Font.draw` 在世界坐标画 mm:ss，带半透明底框

> 注意：`RenderType`/缓冲在 1.21.1 的具体 API（`RenderPipeline`/`RenderType.create`）以调研结果为准。

## 6. Xaero 联动（可选依赖）

- `neoforge.mods.toml` 声明 `xaeroworldmap` / `xaerominimap` 为 optional
- 运行时 `ModList.get().isLoaded(...)` 判定，未安装则 GUI 隐藏"地图联动"入口
- 能力以调研为准；若 API 支持自定义 map renderer → 直接在世界地图画整条轨迹线；否则退化为生成 waypoint 链

## 7. 权限

- 判定：`server.getProfilePermissions(gameProfile) >= requiredOpLevel`（默认 4）
- 或带权限节点（默认 `glowtrack.gui`，LuckPerms 兼容），配置可改；权限串留空 = 仅按 OP 等级
- 单人模式（降级 B）直接放行

## 8. GUI 布局

```
┌────────────────────────────────────────────┐
│ 玩家轨迹追踪            [模式:服务端/本地]  │
│ ┌──────────────┐  ┌──────────────────────┐ │
│ │ 🔍 搜索玩家...│  │ 选中: Notch  [在线]  │ │
│ │ □ Notch   ●  │  │ [▶ 开始追踪][■ 停止] │ │
│ │ □ Steve      │  │ ── 采样设置 ──        │ │
│ │ □ Alex    ○  │  │ 采样间隔 [===|==] 1s │ │
│ │  ...         │  │ 保留天数 [==|====] 7 │ │
│ │              │  │ ── 渲染样式 ──        │ │
│ │              │  │ [✓]热力[✓]发光[✓]穿墙│ │
│ │              │  │ [✓]时间标签[✓]虚线   │ │
│ │              │  │ 配色:[彩虹] 线宽:5.0 │ │
│ │              │  │ [🗺 地图联动]         │ │
│ │              │  │ [⬇ 导出][⬆ 导入]     │ │
│ └──────────────┘  └──────────────────────┘ │
└────────────────────────────────────────────┘
```
- 左：`EditBox` 搜索 + 可滚动玩家列表（头像 + 名字 + 在线状态点）
- 右：选中后展示控制面板；采样/保留为滑块实时生效
- 打开：按键（默认 `K`）+ 命令 `/gt`（或 `/glowtrack`）

## 9. 配置项（ModConfig）

| 键 | 默认 | 说明 |
|---|---|---|
| `sampleIntervalTicks` | 20 | 采样间隔（tick） |
| `minMoveDistance` | 0.0 | 最小记录位移（格），0=只按时间 |
| `turnAngleThreshold` | 25.0 | 拐弯补点的角度阈值（度） |
| `retentionDays` | 7 | 历史保留天数 |
| `maxPointsPerPlayer` | 50000 | 单玩家点数上限 |
| `requiredOpLevel` | 4 | 需要的 OP 等级 |
| `permissionNode` | `glowtrack.gui` | 权限节点（留空=仅 OP） |
| `defaultRenderFlags` | 热力/发光/穿墙/时间/虚线=开 | 渲染默认值 |

## 10. 开发顺序

1. Gradle 骨架 + mods.toml + 主类（能 `gradlew build` 出 jar）
2. 数据模型 + TrackStore（持久化/导入导出，纯逻辑可单测）
3. 网络层 + 服务端记录器
4. 客户端缓存 + 世界内渲染器
5. GUI + 按键 + 命令 + 权限
6. Xaero 联动
7. 配置 + 语言文件 + README
