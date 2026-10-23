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
- **小地图叠加**：注入 `MinimapProcessor#onRender`。注意参数是
  `(PoseStack|GuiGraphics, int, int, int, int, double, int, int, float, CustomVertexConsumers)`，
  描述符是 `IIIIDIIF` —— 曾经写成 `IIIDIIIF`（少一个 int），配合
  `defaultRequire: 0` 导致五个平台**静默失效**：编译过、jar 正常、小地图上没线。
- **世界地图叠加**：1.19.4 / 1.19.2 / 1.18.2 注入 `GuiMap#renderPreDropdown`
  （`(PoseStack;IIIF)V`）——`GuiMap` 在这些版本**不自己声明** `render`（继承自 Screen），
  注入 `render` 必然失败。第一个参数就是 `PoseStack`，所以不需要 MixinExtras 的 `@Local`。
  1.21.8 / 1.21.11 未实现：1.21.11 的 `GuiMap` 虽然声明了 `render`，但
  `GuiGraphics#pose()` 返回的是 2D 的 `Matrix3x2fStack`，拿不到把世界坐标映射到屏幕的
  3D 矩阵，需要按 Xaero 的 GPU-buffer 架构重新设计坐标变换。
  Forge 两版有同样的 `renderPreDropdown` 注入点，但尚未接线。
- 1.21.8 / 1.21.11 的「地图」开关已置灰：留一个点了没反应的按钮比置灰更糟。

## 发布前必查

1. 7 个模块 `build` 全过，`selfTest` 47 条断言全过（零依赖，不需要游戏运行时）。
2. `glowtrack.mixins.json` 的 `client` 列表与实际 `Mixin*.java` 文件**一一对应**。
   空模板（`package` 指向不存在的包）会让 mixin 静默不加载 —— 这个坑踩过一次。
3. **mixin 注入点：一律只写方法名，不带描述符。**

   ```java
   @Inject(method = "onRender", at = @At("TAIL"))   // ✓
   @Inject(method = "onRender(Lcom/mojang/...;IIII...)V", ...)  // ✗
   ```

   带描述符有两个独立的坑，都在 `defaultRequire: 0` 下静默回滚：

   - **`remap = false` 时描述符不重映射。** 注入第三方 mod（Xaero）必须 `remap = false`，
     此时描述符里的 Minecraft 类名保持编译期写法（mojmap），而 Fabric 运行时是
     intermediary（`net/minecraft/class_4587`）—— 永远对不上。refmap 也是空的
     （`{"mappings":{}}`），因为 remap=false 压根不生成映射。
   - **描述符必须用 JVM 内部名（斜杠），写成点号直接解析失败。**
     这一条踩过：两个 Forge 模块的同一处代码，一个写 `Lcom/mojang/...`（对），
     一个写 `net.minecraft.client.gui.GuiGraphics`（点号，非法）。

   只写方法名就没有描述符可写错，参数类型由 knot/ModLauncher 在加载 mixin 类时统一映射。
   **前提是目标方法无重载**——有重载时必须用描述符，那就得写运行时的名字（Fabric 用
   intermediary、Forge 用 mojmap），并逐个平台核对，风险高得多。

4. Forge 的 `mods.toml` 里 Xaero 依赖必须是 `mandatory = false`，否则没装 Xaero 的
   玩家会被拦在启动画面。
5. Forge 的 jar 必须已 reobf：扫一遍 class 应当只剩 SRG 方法名（`m_xxxxx_`），
   出现 Mojang 原名说明拿到的是 dev 产物，正式服会崩。
6. jar 的 `META-INF` 里不应残留 `neoforge.mods.toml`（母版遗留，占位符未展开）。
7. 语言文件键完整：源码里的 `Text.s/tr`、`translatable` 键都要在
   `en_us.json` / `zh_cn.json` 里，缺键会直接显示原始 key。
8. 客户端发包要有 try/catch：`ClientPlayNetworking.send` 在通道未协商时抛异常，
   而它是在 GUI 点击回调里同步调用的 —— 没有兜底就是"点一下按钮崩溃客户端"。
9. **仓库里不能出现任何 API token**（Modrinth / GitHub）。上传脚本一律读环境变量
   （`MODRINTH_TOKEN`），缺失就 `throw`，不要写"有默认值"的兜底。
   本仓库是公开的，**一旦明文提交过就必须视为泄露并立刻轮换** ——
   删掉当前文件、甚至重写 git 历史都救不回来（旧 commit、fork、缓存里仍有）；
   唯一有效的动作是去服务商后台撤销旧 token。
   拆分写法（`'mrp_' + 'xxx'`）只是躲开了 grep，不提供任何安全性。

## 脚本编码

仓库里的 `.ps1` 一律存成 **UTF-8 with BOM**。PowerShell 5.1 会把无 BOM 的脚本
按 GBK 解析，中文注释会让引号配对错乱，报"字符串缺少终止符"之类的假语法错
—— 文件本身没问题，是解码方式错了。用 node 写文件再加 BOM，别用
`Set-Content -Encoding utf8`（它加的 BOM 位置和时机容易和生产工具链打架）。
