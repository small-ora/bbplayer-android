# PLAN — UI 高级感改造（v9.0 / v9.1 / v9.2）

> SPEC：[SPEC.md](SPEC.md)（冻结于 `SPEC_BASE = c7303f8`）；本文件**不冻结**，执行记录在实现过程中追加。
> 分支：`feat/ui-premium-p0`；基线 `BASE = c366cc7`；交付方式：本地提交 + 验收包，**不 push / 不合入**。

## 0. 总路线

| 版本 | 对应清单 | 目标 | 估时 | 验收物 |
|---|---|---|---|---|
| **v9.0（本轮）** | P0-1 ~ P0-6 | 修可读性/材质/热区六个硬伤，建立中性 token 与播放页亮度守卫 | 1–2 天 | 本地提交 + 单测证据 + 静态断言 + 模拟预览 + 验收包 |
| v9.1 | P1-1 ~ P1-5 | 建立「系统感」：字阶 7 档、间距/圆角/图标 token、底部 dock、深度体系、骨架/空态 | 3–5 天 | 同上 + 三主题 × 4 页截图（需设备） |
| v9.2 | P2-1 ~ P2-6 | 拉满「高级感」：动效体系、发现页 hero、设置页、我的页、触感、图标 | 1–2 周 | 同上 + 转场录屏（需设备） |

依赖关系：`v9.0 → v9.1 → v9.2`（v9.1 的字阶/dock 依赖 v9.0 的中性 token；v9.2 的动效依赖 v9.1 的组件结构）。  
每版结束 = 一次人验收门禁；未获通过不进入下一版。

## 1. v9.0（P0）执行步骤

> 每步：改 → QUICK 绿 → 本地提交（一主题一 commit）。失败按 skill 失败协议（2 次停手分析，3 次换上下文）。

| 步 | 主题 | 写范围 | 验证 | 回滚点 |
|---|---|---|---|---|
| S1 | 隔离 + SPEC 冻结 | `.ai/ui-premium/` | `git log` 见 SPEC 提交 | 删除分支 |
| S2 | 中性 token + 材质边界（P0-3/4 基础） | `ui/theme/ThemeTokens.kt`(新)、`BbTheme.kt`、`DesignTokens.kt` | QUICK + `ThemeTokensTest` | S1 |
| S3 | 文本 alpha 收敛（P0-3） | `Headers.kt`、`States.kt`、`MiniPlayer.kt`、`HomeScreen.kt`、`ProfileScreen.kt`、`CommentsScreen.kt`、`LoginScreen.kt`、`ImportPlaylistScreen.kt` | QUICK + grep 分类 | S2 |
| S4 | 播放页背景三层化 + 亮度守卫（P0-1） | `ui/theme/PlayerBackdrop.kt`(新)、`PlayerScreen.kt`、`ui/theme/AlbumWash.kt`、`app/src/test/.../PlayerBackdropTest.kt`(新) | AFFECTED（单测）+ QUICK | S3 |
| S5 | 状态栏图标跟随（P0-2） | `PlayerScreen.kt` | QUICK + 代码检查 | S4 |
| S6 | 弹层 token 化（P0-5） | `BbTheme.kt`、`PlayerScreen.kt` | QUICK + grep `FF262626` = 0 | S5 |
| S7 | 热区 ≥48dp（P0-6） | `Headers.kt`、`PlayerScreen.kt`、`SeekLine.kt`、`TrackRow.kt`、`SectionHeader.kt` | QUICK + grep | S6 |
| S8 | 自检 FULL gate | `.ai/ui-premium/evidence/` | `data/playback/lyrics/app` 单测 + `assembleDebug` | S7 |
| S9 | 独立审查（fresh reviewer） | `.ai/ui-premium/REVIEW.md` | reviewer 返回 P0/P1 处置 | S8 |
| S10 | 验收包 + 模拟预览 | `.ai/ui-premium/ACCEPTANCE.md`、`docs/ui-review/p0-preview-*.png` | 人验收 | S9 |

### 写范围与并行

- 本版**由 Lead 串行实现**：S2/S3 与 S4 都会动 `PlayerScreen.kt`（S3 的 `subText` 与 S4 的背景层相邻），并行会冲突；不派写手，仅派 **1 名 fresh reviewer**（S9，只读）。
- 共享文件（`BbTheme.kt`、`PlayerScreen.kt`、`DesignTokens.kt`）由 Lead 统一改。

### 人工门禁

- S8 后若 FULL 红 → 不进 S9。
- S9 的 P0/P1 findings 必须修复或写明不修理由。
- S10 由人给「通过 / 修改后通过 / 打回 / 无法验收」，AI 不自宣通过。

### 重分级触发（出现即停，回 SPEC）

- 需要改播放/网络/数据链路或新增依赖；
- 需要动 `PlayerScreen` 的 Pager/评论/歌词逻辑（非纯视觉）；
- 主题改动导致某套主题需要额外组件（例如必须拆 dock），即超出 v9.0 范围；
- 无设备导致的 BLOCKED 影响验收结论（需人决定是否接受静态验证）。

## 2. v9.1 计划（P1，待 v9.0 验收通过后启动）

| 步 | 内容 | 主要文件 | AC 要点 |
|---|---|---|---|
| S11 | 字阶 7 档 + 行高 + `tnum`；页面内字号字面量清零 | `BbTheme.kt`、`Headers.kt`、全部 screens 的 `fontSize` | 字号字面量只在 theme 包；时间列等宽 |
| S12 | 间距/圆角/图标 token：4/8/12/16/20/24/32/48；8/12/16/28/full；图标 16/20/24 + 48 热区 | `DesignTokens.kt`、`Headers.kt`、各组件 | 非 token 的 dp 字面量只允许出现在 theme/ |
| S13 | 底部 dock 合并：mini 行 + icon-only 导航同一容器，去 pill/label，选中 fill | `AppRoot.kt`、`MiniPlayer.kt`、`BottomNavBar.kt` | 单一 28–32dp 圆角容器；无 M3 pill |
| S14 | 深度体系：卡片 veil/白面 + hairline 统一；封面 24dp 环境阴影 + 光晕；dock 16dp 软阴影 | `BbTheme.kt`、`CoverImage.kt`、`PlayerScreen.kt` | 全应用仅封面/dock/Sheet 有阴影 |
| S15 | 骨架屏 + 图片 placeholder/crossfade + 空态/错误态重设计 | `States.kt`、`CoverImage.kt`、各列表页 | 首屏无全屏转圈；图片无灰块瞬变 |

## 3. v9.2 计划（P2，待 v9.1 验收通过后启动）

| 步 | 内容 | 主要文件 |
|---|---|---|
| S16 | 动效 token（120/220/320 + FastOutSlowIn）+ 页面转场 + tab Crossfade + 列表 `animateItem` | `AppRoot.kt`、`ui/theme/Motion.kt`(新) |
| S17 | mini → 播放页封面 `SharedTransitionLayout` 共享元素 | `AppRoot.kt`、`PlayerScreen.kt`、`MiniPlayer.kt` |
| S18 | 发现页 hero（继续收听）+ 模块高度分级 + 间距节奏 | `HomeScreen.kt` |
| S19 | 设置页：accent 分组标签 + 前导图标 + 56dp 行 + 危险项 + 版本收尾 | `SettingsScreen.kt` |
| S20 | 我的页 hero 合并 + 热力图空态 | `ProfileScreen.kt` |
| S21 | 触感三件套 + 图标体系（Material Symbols Rounded / 自绘 glyph） | 全组件 |

## 4. 执行记录（实现中追加）

| 步 | 状态 | commit | 验证证据 |
|---|---|---|---|
| S1 | ✅ SPEC 冻结 | `c7303f8` | `git log` |
| S2 | 待办 | | |
| S3 | 待办 | | |
| S4 | 待办 | | |
| S5 | 待办 | | |
| S6 | 待办 | | |
| S7 | 待办 | | |
| S8 | 待办 | | |
| S9 | 待办 | | |
| S10 | 待办 | | |
