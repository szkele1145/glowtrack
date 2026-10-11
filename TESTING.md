# GlowTrack 测试指南

## 当前环境状态

| 项 | 状态 |
|---|---|
| 模组 jar | `build\libs\glowtrack-1.0.0.jar` (71 KB) |
| 测试服务端 | ✅ 运行中，端口 `25565`（离线模式） |
| 测试客户端 | ✅ 运行中（gradle runClient） |
| 测试身份 | `kele_jiabin`（OP 4 级 + 已加白名单） |

## 立即试玩

客户端已在运行。若已在单人世界，退出到 **多人游戏 → 添加服务器 → localhost** 进入测试服。

**进服后：**

1. **走一段路** —— 服务端会自动记录你在白名单内的移动轨迹
2. **按 `K`** 打开追踪界面（或 `/gt open`）
3. 左侧列表点自己的名字 `kele_jiabin` → 右侧点 **▶ 开始追踪**
4. 轨迹线立刻在世界里渲染（彩虹热力 + 发光 + 穿墙）
5. **拖动时间轴**回看点 → 点 **📍 传送至该时刻**
6. 右侧开关切换：速度热力 / 发光光晕 / 穿墙显示 / 时间标签 / 脉冲虚线

## 想测多人追踪？

服务端是离线模式，可再开第二个客户端用别的名字（如 `TestBot`）进服走动，
再用 `kele_jiabin` 追踪 `TestBot` 的轨迹。第二个客户端启动：
```powershell
powershell -File D:\player-tracker\run_client.ps1
```
（注意：两个客户端需不同游戏名，可在客户端启动器里改；runClient 默认同名会冲突）

## 环境细节

- **工程**：`D:\player-tracker`（ModDevGradle 2.0.78 + NeoForge 21.1.248 + Java 21）
- **轨迹数据**：服务端存于 `run\glowtrack\<uuid>\<yyyy-MM-dd>.json`
- **配置**：`run\config\glowtrack-common.toml`（采样间隔/保留天数/OP等级等）
- **启动脚本**：
  - 服务端 `run_server.ps1` ｜ 客户端 `run_client.ps1` ｜ 打包 `build_jar.ps1`

## 常用操作

```powershell
# 停止测试环境
Stop-Process -Id 11096 -Force   # 服务端 java
Stop-Process -Id 25856 -Force   # 客户端

# 重新打包
powershell -File D:\player-tracker\build_jar.ps1
```

## 已实现功能清单

- [x] 服务端记录白名单玩家轨迹（tick 采样 + 拐弯补点）
- [x] 双模式：服务端记录 / 纯客户端降级本地观察
- [x] 文件持久化（按玩家+日期）+ 导入导出 + 保留清理
- [x] OP4级 + LuckPerms 权限节点（默认解析器回退 OP）
- [x] 白名单列表 + 搜索框 GUI
- [x] 按需追踪：点开始追踪才加载显示
- [x] 时间轴回看 + 一键传送至任意时刻（含跨维度）
- [x] 世界内 3D 轨迹渲染：彩虹热力/发光/穿墙/时间标签/脉冲虚线
- [ ] Xaero 地图联动（Mixin GuiMap）—— 见下

## 待办：Xaero 世界地图画线

Xaero 无官方画线 API，需 Mixin `xaero.map.gui.GuiMap#render`（参考 XaeroPlus@1.21 做法）。
jar 已就位 `libs\xaeroworldmap-neoforge-1.21.1-1.47.0.jar`（compileOnly）。
风险：Xaero 小版本升级可能改变内部方法签名，需做回归测试。
