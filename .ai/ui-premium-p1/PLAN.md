# PLAN — UI 高级感 P1（v9.1）

> SPEC：[SPEC.md](SPEC.md)（冻结于 `SPEC_BASE = 157ef3b`）；本文件**不冻结**，执行记录随实现追加。
> 分支：`feat/ui-premium-p1`（worktree `.agents/worktrees/feat/ui-premium-p1`）；`BASE = 93c8a07`（P0 HEAD）。
> 交付方式：本地提交 + 验收包，**不 push / 不合入**。
> 变基记录：P0 真机验收通过并发布 v0.9.0 后，本分支已 `git rebase main`（main@`7ff362a`）；上表哈希为变基后哈希。

## 0. 依赖与并行

- P1 依赖 P0 已提交的 `ThemeTokens` / `PlayerBackdrop` / `BbTheme`；**不回写 P0 分支**。
- 另一会话继续在 `/root/bbplayer` 做 P0 真机验收；本任务只改 worktree，避免共享工作区冲突。
- 共享资源：Gradle 可并发；设备截图须等 P0 会话释放 adb，否则视觉 AC 标 BLOCKED。
- 角色：**Lead 串行实现**（S11/S12 与 S13/S14/S15 都会改 `BbTheme.kt`、`DesignTokens.kt`、`AppRoot.kt`、`CoverImage.kt`，并行写手会冲突），另派 **1 名 fresh reviewer**（只读）。

## 1. 执行步骤

| 步 | 主题 | 写范围 | 验证 | 回滚点 |
|---|---|---|---|---|
| S11 | 字阶 7 档 + tnum + 页面 fontSize 清零 | `ui/theme/Typography.kt`(新)、`BbTheme.kt`、`app/src/test/.../BbTypeTest.kt`(新)、全部 `screens/`+`components/` 的 fontSize 字面量 | QUICK + `BbTypeTest` + grep 断言 | `157ef3b` |
| S12 | 间距/圆角/图标 token 收敛 | `DesignTokens.kt`、`Headers.kt`、`BottomNavBar.kt`、`MiniPlayer.kt`、各 screens 的 padding/圆角/图标尺寸 | QUICK + grep 报告 + 例外清单 | S11 |
| S13 | 底部 dock 合并 | `AppRoot.kt`、`MiniPlayer.kt`、`BottomNavBar.kt`、`DesignTokens.kt` | QUICK + grep `NavigationBar` = 0 + 截图/模拟图 | S12 |
| S14 | 深度体系 | `BbTheme.kt`、`CoverImage.kt`、`PlayerScreen.kt`、`MiniPlayer.kt`(dock 阴影) | QUICK + `shadow(` 白名单 + 截图/模拟图 | S13 |
| S15 | 骨架屏 + 图片占位 + 空/错误态 | `States.kt`、`CoverImage.kt`、`HomeScreen.kt`、`LibraryScreen.kt`、`HistoryScreen.kt`、`DownloadsScreen.kt`、`PlayerScreen.kt` | QUICK + grep 转圈 + 截图/模拟图 | S14 |
| S16 | 自检 FULL gate | `.ai/ui-premium-p1/evidence/` | 四模块单测 + `assembleDebug` | S15 |
| S17 | 独立审查 | `.ai/ui-premium-p1/REVIEW.md` | fresh reviewer P0/P1 处置 | S16 |
| S18 | 验收包 | `.ai/ui-premium-p1/ACCEPTANCE.md` | 人验收 | S17 |

## 2. 人工门禁

- S16 若 FULL 红 → 不进 S17。
- S17 的 P0/P1 findings 必须修复或写明不修理由。
- 视觉类 AC（dock/深度/骨架）无真机时标 BLOCKED 并给模拟图示意。
- S18 由人给「通过 / 修改后通过 / 打回 / 无法验收」，AI 不自宣通过。

## 3. 重分级触发（出现即停，回 SPEC）

- 需要新增依赖（如 shimmer、新图标库）或改版本号；
- 需要改播放/网络/数据链路，或动 Pager/歌词逻辑（非纯视觉）；
- 需要改 P0 已冻结的 SPEC/中性色数值；
- 设备不可用导致视觉结论无法给出，需人决定是否接受静态验证。

## 4. 执行记录（实现中追加）

| 步 | 状态 | commit | 验证证据 |
|---|---|---|---|
| S1 隔离 | ✅ worktree `feat/ui-premium-p1` @ `93c8a07` | — | `git worktree list` |
| S2 SPEC 冻结 | ✅ | `157ef3b` | `git log` |
| S11 | ✅ 字阶 7 档 + tnum（21 处）+ fontSize 字面量清零 | `3f5100b` | QUICK 绿；`BbTypeTest` 5/5；grep 页面 fontSize 字面量 = 0；app 单测绿 |
| S12 | ✅ 间距/圆角/图标 token + 中间值消除 | `ddc0d7b` | QUICK 绿；`static-assertions-s12.log`：padding 字面量仅剩 7 处结构值（已豁免）、Icon 内仅剩 44/40/56 大图标、`size*0.5f` 清零 |
| S13 | ✅ 底部 dock 合并（迷你行 + icon-only 导航） | `6f009a3` | QUICK 绿；`NavigationBar` 引用 0；dock token 见 `DesignTokens`；设备截图待 P0 会话释放 |
| S14 | ✅ 深度体系：卡片 hairline / 封面 24dp 阴影+光晕 / dock 16dp 阴影 | `f60b99a` | QUICK 绿；`shadow(` 仅剩 MiniPlayer 悬浮态 + Depth.kt；21 处卡片补 0.5dp hairline |
| S15 | ✅ 骨架屏 + 图片占位/淡入/错误兜底 + 空/错误态 | `f60b99a` | QUICK 绿；10 处首屏 loading 换骨架；CoverImage 200ms 淡入 + onError 兜底；ErrorState 内联卡片 |
| S16 | ✅ FULL gate + 真机冒烟（MI 6 三主题 11 张截图） | `a6f0001` | `evidence/full-gate.log`（EXIT=0，641 单测 0 fail 0 skipped）+ `evidence/device/p1-*.png`；骨架动画已提升为列表级单条 |
| S17 | ✅ 2 名 fresh reviewer（P0×0 / P1×3）+ 修复复核 | `15a0c82`/`eaf4217`/`ff76633` | `REVIEW.md`；P1 全修，P2 修 8 条/记录 3 条 |
| S18 | ✅ 验收包 v1（含 2 名 reviewer 处置、真机三主题截图） | — | `ACCEPTANCE.md`；HEAD `ff76633`，FULL gate EXIT=0 |
