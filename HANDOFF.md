# 交付说明（2026-10-23）

## 这次做了什么

把 GlowTrack 和 WorldReplay 移植到 7 个新平台，并做了发布前的静态验证。

### 平台矩阵

| 模组 | 平台 | 产物 | 位置 |
|---|---|---|---|
| GlowTrack 1.4.8 | Fabric 1.18.2 / 1.19.2 / 1.19.4 / 1.21.8 / 1.21.11 | 7 个 jar | `D:\tools\gt_dist` |
| | Forge 1.19.4 / 1.20.1 | | |
| WorldReplay 0.3.6 | Fabric 同 5 个 + Forge 同 2 个 + NeoForge 1.21.1 | 9 个 jar | `D:\tools\wr_dist` |

代码位置：`D:\tools\gt_src`（glowtrack，`main`）、`F:\vibe coding工程\sfx\worldreplay`（echoworld，`main`）。

每个平台的构建脚本、`mixins.json`、`mods.toml`/`fabric.mod.json` 都是独立的，源码树不共享 core —— 原因和差异清单见同目录 `PORTING.md`。

### 验证结果

| 项目 | GlowTrack | WorldReplay |
|---|---|---|
| 构建 | 7/7 BUILD SUCCESSFUL | 9/9 已有产物 |
| 自测 | 40 条 ×7，经 Gradle 跑 | core 65 条，`javac`+`java` 直跑 |
| mixin 注入点 | javap 逐版本核对真实依赖 jar | 同 |
| 网络兜底 | 7/7 有 try/catch | 9/9 有 |
| Forge reobf | 250 处 SRG 名、0 处 mojmap 名 | — |

## 关键发现：一批"编译通过但从未生效"的功能

这类问题的共同成因是 **Mixin 失败被吞掉**或 **异常被静默捕获**，症状都是"看起来正常，功能没有"。

前五条是静态核对抓到的（javap 对签名、扫 jar 内容、比对 mixin 注册表）。
**后两条只有真机 `runServer` 才抓得到**，而且它们比前五条严重得多——前五条是"某个功能不生效"，
后两条是"整个模组加载不起来"，而编译、自检、jar 结构全都正常。

1. **五个模块的小地图叠加从未生效**（1.18.2 / 1.19.2 / 1.19.4 / Forge 1.19.4 / Forge 1.20.1）
   `MinimapProcessor#onRender` 的描述符写成 `IIIDIIIF`，实际是 `IIIIDIIF`（4 个 int 才轮到 double）。
   Mixin 按描述符查方法，差一个字符就永远匹配不上；`defaultRequire: 0` 让它静默回滚。

2. **三个模块的世界地图叠加注入了一个不存在的方法**（1.18.2 / 1.19.2 / 1.19.4）
   注入 `GuiMap#render`，但 `GuiMap` 在这些版本不声明 render（继承自 Screen），Mixin 只看本类。
   已改注入 `GuiMap#renderPreDropdown(PoseStack;IIIF)V`，顺带去掉 MixinExtras 依赖。

3. **三个模块的客户端发包没有异常兜底**（1.21.11 / 1.21.8 / 1.19.4）
   `ClientPlayNetworking.send` 在通道未协商时抛异常，而它在 GUI 点击回调里同步执行 ——
   点一下按钮就崩客户端。

4. **1.21.8 / 1.21.11 的 Xaero 代码是死的**
   `mixins.json` 是空模板（package 指向不存在的包），`MixinGuiMap` 编进 jar 但从未加载，
   而描述里写着 "Xaero map integration"、GUI 开关还能点。已删死代码、改描述、开关置灰。

5. **Forge jar 里混着 `neoforge.mods.toml`**（占位符未展开的母版遗留）。

6. **Fabric 入口类用 private 构造器 —— 模组完全无法加载**（五个 Fabric 平台全部）
   模板里留的是 `private GlowTrackFabric() {}`（工具类的习惯写法）。Fabric Loader 用反射
   `newInstance()` 创建 entrypoint，构造器不可访问就抛：

   ```
   IllegalAccessException: DefaultLanguageAdapter cannot access a member of
   class com.glowtrack.fabric.GlowTrackFabric with modifiers "private"
   ```

   服务端表现是 `Failed to start the minecraft server / Could not execute
   entrypoint stage 'main'`。**已发布到 Modrinth 的五个 Fabric 版本一个都起不来。**
   入口类的构造器必须是 public。

7. **Forge 把 FORGE 总线事件挂到 MOD 总线 —— 模组加载中止**（两个 Forge 平台）
   `Permissions` 标注 `bus = Bus.MOD`（从 NeoForge 模板继承）。NeoForge 21.x 会按参数类型
   自动分流，所以一个类可以同时挂两个总线的订阅；**Forge 1.20.1 没有这个能力**，
   一个 `@EventBusSubscriber` 类只能属于一个总线，方法参数必须来自该总线：

   ```
   IllegalArgumentException: Permissions.gatherNodes(PermissionGatherEvent$Nodes)
   has @SubscribeEvent annotation, but takes an argument that is not a subtype
   of interface net.minecraftforge.fml.event.IModBusEvent
   ```

   `PermissionGatherEvent` 属于 FORGE 总线，所以去掉了 `bus = MOD`。
   移植 NeoForge 代码到 Forge 时，**逐个核对每个 `@EventBusSubscriber` 类里订阅方法的
   参数类型是否属于同一个总线**，这是最容易漏的一类。

复验方式（改动网络 / GUI / 事件层之后必做）：

```
cd <模块目录>
# run/eula.txt 写 eula=true，run/server.properties 换个不冲突的端口
gradle runServer    # 看到 "Done (x.xxxs)!" 才算加载成功
```

## 安全事件：API token 泄露

两个仓库都是 **public**，上传脚本里的 Modrinth token 是明文（其中一个用了
`'mrp_' + '...'` 的拆分写法，只躲 grep 不提供安全性）。

- 已从工作区清除：glowtrack `d222599`、echoworld `20d16de`，四个+三个脚本改为读
  `MODRINTH_TOKEN` 环境变量
- **但 git 历史里仍有旧值，删不掉**（commit 是完整快照）
- **必须去 https://modrinth.com/settings/account → API Tokens 撤销并重新生成**

在换 token 之前不要发布任何东西。

## 待办

1. **撤销并轮换 Modrinth token**（阻塞发布）
2. **决定是否发布**：GlowTrack 项目 `mpdIYgGq` 目前 0 个版本（全新发布 7 个）；
   WorldReplay `SvKPzm09` 补 0.3.6 的 9 个
   → 脚本：`D:\tools\gt_src\upload_gt_platforms.ps1`（支持 `-DryRun`，已干跑验证）
3. **真机启动验证**：所有结论都来自编译 + 自测 + javap，没有一次真实启动。
   优先试 **1.18.2**（`TextComponent`、手算 scissor、`registerTexture`、老通道网络，
   偏离母版最远）和 **1.19.2**（同代但另一套网络写法）。
4. **Xaero 叠加的视觉效果**：注入点已确认存在，但 `renderPreDropdown` 在 Xaero 渲染流程
   里的位置无法离线确认，叠加层可能被地图内容遮住。

测试服务器（25565/25566/25567）全程未启动。

## 已知功能降级（发布页应如实写）

| 平台 | 小地图叠加 | 世界地图叠加 | 双层渲染 |
|---|---|---|---|
| 1.21.11 / 1.21.8 | ✗ | ✗ | ✓ |
| GlowTrack 1.19.4 | ✓ | ✓ | ✓ |
| GlowTrack 1.19.2 / 1.18.2 | ✓ | ✓ | ✗ 单层 |
| GlowTrack Forge 1.20.1 / 1.19.4 | ✓ | ✗ | ✗ 单层 |

- 单层渲染：1.20.1 及更早的 `RenderStateShard` 常量是包内可见，构造不出自定义
  `CompositeState`，两层退到 `RenderType.lightning()`。轨迹照画，失去"主线半透明 +
  光晕更亮"的层次。
- 1.21.11 的世界地图叠加：`GuiGraphics#pose()` 返回 2D 的 `Matrix3x2fStack`，
  拿不到把世界坐标映射到屏幕的 3D 矩阵，需按 Xaero 的 GPU-buffer 架构重设计。
