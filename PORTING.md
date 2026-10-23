# GlowTrack 多平台移植说明

本仓库为同一个模组维护 7 个平台的源码树。**没有共享 core 模块** —— 每个模块都
包含完整的 `com.glowtrack` 源码。这是刻意的：各版本 Minecraft / 加载器的 API
差异太大（见下表），抽公共层反而会被迫加一层薄适配壳，出问题时还要跨两层排查。

改一处逻辑需要同步到 7 份代码时，请逐平台按下面的差异清单核对，**不要整份覆盖**。

## 平台与产物

| 模块 | MC | 加载器 | 产物 |
|---|---|---|---|
| `fabric-1.21.11` | 1.21.11 | Fabric (loom 1.13.6) | `glowtrack-<ver>-1.21.11-fabric.jar` |
| `fabric-1.21.8` | 1.21.8 | Fabric (loom 1.11.8) | `glowtrack-<ver>-1.21.8-fabric.jar` |
| `fabric-1.19.4` | 1.19.4 | Fabric (loom 1.9.2) | `glowtrack-<ver>-1.19.4-fabric.jar` |
| `fabric-1.19.2` | 1.19.2 | Fabric (loom 1.9.2) | `glowtrack-<ver>-1.19.2-fabric.jar` |
| `fabric-1.18.2` | 1.18.2 | Fabric (loom 1.9.2) | `glowtrack-<ver>-1.18.2-fabric.jar` |
| `forge-1.20.1` | 1.20.1 | Forge 47.2.0 (FG 6) | `glowtrack-<ver>-1.20.1-forge.jar` |
| `forge-1.19.4` | 1.19.4 | Forge 45.4.5 (FG 6) | `glowtrack-<ver>-1.19.4-forge.jar` |

Fabric 模块用 `gradle-9.4.1`；Forge 模块必须用 `gradle-8.10.2`（ForgeGradle 6
不支持 Gradle 9）。两者都设 `GRADLE_USER_HOME=D:\tools\gradle-home`。

## 网络层三套实现

| 平台 | 机制 |
|---|---|
| 1.21.11 / 1.21.8 | `CustomPacketPayload` + `StreamCodec`（NeoForge `PayloadRegistrar` 那套） |
| 1.19.4 | Fabric `FabricPacket` + `PacketType` |
| 1.19.2 / 1.18.2 | 老通道模型：`ResourceLocation` + `FriendlyByteBuf`，自建 `Packet` 接口 |
| Forge 1.20.1 / 1.19.4 | `SimpleChannel` + 单一 `Envelope` 信封（`[通道 id][长度][负载]`） |

要点：

- **字段顺序在四处必须完全一致**，否则同版本跨加载器互联会错位。
- 1.19.2 / 1.18.2 的接收器跑在网络线程，服务端处理器一律先 `server.execute(...)`。
- Forge 的 `Envelope` 解码对声明长度做钳制（4 MiB），并只读「声明长度」与
  「实际可读长度」的较小值 —— 否则恶意包能直接撑爆接收端内存。
- Forge 通道用 `NetworkRegistry.newSimpleChannel(..., v -> true, v -> true)`：
  对端没装本模组时版本是 `ABSENT`，做版本校验会把玩家踢出而不是让他正常进服。

## GUI 三代 API

| 平台 | 绘制上下文 | 填充 | 文本 | 皮肤 |
|---|---|---|---|---|
| 1.21.11 / 1.21.8 / Forge 1.20.1 | `GuiGraphics` | `g.fill(...)` | `g.drawString(font, ...)` | `PlayerSkin`（Forge 1.20.1 用 `ResourceLocation`） |
| 1.19.4 / 1.19.2 / 1.18.2 / Forge 1.19.4 | `PoseStack` | `GuiComponent.fill(g, ...)` | `font.draw(g, ...)` | `ResourceLocation` |
| 1.18.2 额外 | — | — | `new TextComponent` / `new TranslatableComponent`（无静态工厂） | `getInsecureSkinInformation` + `registerTexture` |

控件层还有两代差异，容易漏：

- 1.19.3+ 是 `renderWidget(PoseStack, int, int, float)` + `getX()/getY()` + `isHovered()`。
- 1.19.2 / 1.18.2 是 `render(...)`（Button 子类走 `renderButton`）+ **public 字段** `x`/`y`
  + `isHovered` **字段**；叙述钩子是 `updateNarration()`（public），不是
  `updateWidgetNarration()`；`Button` 构造没有 `DEFAULT_NARRATION` 参数；
  `EditBox` 没有 `setHint()`。

`renderBackground` 的参数个数也在变：1.19.x 只收 `PoseStack`，1.20.1 只收
`GuiGraphics`，1.20.2+ 才带鼠标坐标。因此**悬停更新一律放在 `render()` 里**，
别写在 `renderBackground`。

## 已知功能降级（发布前请照实说明）

| 平台 | 小地图叠加 | 世界地图叠加 | 双层渲染（主线 + 光晕） |
|---|---|---|---|
| 1.21.11 / 1.21.8 | ✗ | ✗ | ✓ |
| 1.19.4 / 1.19.2 / 1.18.2 | ✓ | ✓ | ✓（1.19.4）/ ✗（1.19.2、1.18.2） |
| Forge 1.20.1 / 1.19.4 | ✓ | ✗ | ✗ |

- **双层渲染**：`RenderStateShard` 的 shader / transparency / cull 常量在 1.20.1 及更早
  是包内可见，外部构造不出自定义 `CompositeState`；`debugQuads()` 也要到 1.19.3 才有。
  这些平台两层都退到 `RenderType.lightning()` —— 轨迹照画、宽度和颜色都对，但失去
  「主线半透明 + 光晕更亮」的层次分离，叠加后整体更亮。
- **世界地图叠加**：1.21.8 / 1.21.11 需要按 Xaero 的 GPU-buffer 架构重写；Forge 两版
  因为 Xaero 不在 `GuiMap` 自己声明 `render`，原 `@Inject` + MixinExtras `@Local`
  抓 `PoseStack` 无法验证，已放弃（因此也不需要 MixinExtras 依赖）。
- 1.21.8 / 1.21.11 的「地图」开关已置灰：留一个点了没反应的按钮比置灰更糟。

## 发布前必查

1. 7 个模块 `build` 全过，`selfTest` 40 条断言全过（零依赖，不需要游戏运行时）。
2. `glowtrack.mixins.json` 的 `client` 列表与实际 `Mixin*.java` 文件**一一对应**。
   空模板（`package` 指向不存在的包）会让 mixin 静默不加载 —— 这个坑踩过一次。
3. Forge 的 `mods.toml` 里 Xaero 依赖必须是 `mandatory = false`，否则没装 Xaero 的
   玩家会被拦在启动画面。
4. Forge 的 jar 必须已 reobf：扫一遍 class 应当只剩 SRG 方法名（`m_xxxxx_`），
   出现 Mojang 原名说明拿到的是 dev 产物，正式服会崩。
5. jar 的 `META-INF` 里不应残留 `neoforge.mods.toml`（母版遗留，占位符未展开）。
6. 语言文件键完整：源码里的 `Text.s/tr`、`translatable` 键都要在
   `en_us.json` / `zh_cn.json` 里，缺键会直接显示原始 key。
