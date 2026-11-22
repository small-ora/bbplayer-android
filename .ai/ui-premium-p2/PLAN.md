# PLAN — UI 高级感 P2（v9.2）

> SPEC：[SPEC.md](SPEC.md)（冻结于本提交）；`BASE = feat/ui-premium-p1`（实现开始时 rebase 到其最新 S15 提交）
> 分支：`feat/ui-premium-p2`（worktree `.agents/worktrees/feat/ui-premium-p2`）
> 交付：本地提交 + 真机截图/录屏证据 + 验收包；**不 push / 不合入**（发布由人另行授权）

## 0. 依赖与并行

- 依赖 v9.1 已完成 S13（dock）/S14（深度）/S15（状态）并提交；**v9.2 开工前先 `git rebase feat/ui-premium-p1`**。
- v9.1 会话继续收尾（gate/review/acceptance）时不改同一分支；v9.2 只在自己的 worktree 写。
- 设备（MI 6 / Android 14）由本会话占用做真机截图/录屏；与 v9.1 会话约定不并发 adb。
- 角色：Lead 串行实现 + 1 名 fresh reviewer（只读）。

## 1. 执行步骤

| 步 | 主题 | 写范围 | 验证 | 回滚点 |
|---|---|---|---|---|
| S16 | 动效 token + 页面转场 + tab Crossfade + 播放态/进度/列表动画 + 跑马灯边缘 | `ui/theme/Motion.kt`(新)、`AppRoot.kt`、`PlayerScreen.kt`、`MiniPlayer.kt`、`QueueSheet.kt`、`PlaylistDetailScreen.kt` | ✅ QUICK 绿 + `BbMotionTest` 3/3 + 真机（MI 6）截图 + `s16-transitions.mp4` 录屏 | `fbea5ef` |
| S17 | mini→播放器封面 shared element | `AppRoot.kt`、`MiniPlayer.kt`、`PlayerScreen.kt` | QUICK + 真机 10 次进出 + 抽帧 | S16 |
| S18 | 发现页 hero + 模块高度/间距分级 | `HomeScreen.kt` | QUICK + 三主题截图（空/非空历史） | S17 |
| S19 | 设置页 accent 分组标签 + 前导图标 + 56dp 行 + 危险项 + 版本卡 | `SettingsScreen.kt` | QUICK + 逐组点按回归 + 三主题截图 | S18 |
| S20 | 我的页 hero 合并 + 热力图空格可见 | `ProfileScreen.kt` | QUICK + 空/有数据截图 | S19 |
| S21 | 触感三件套 + 图标族/尺寸收敛 | 组件与相关 screen | QUICK + 调用点清单 + 截图 | S20 |
| S22 | 自检 FULL gate | `.ai/ui-premium-p2/evidence/` | 四模块单测 + assembleDebug | S21 |
| S23 | 独立审查（fresh reviewer） | `.ai/ui-premium-p2/REVIEW.md` | P0/P1 处置 | S22 |
| S24 | 验收包 | `.ai/ui-premium-p2/ACCEPTANCE.md` | 人验收 | S23 |

## 1.1 执行记录

| 步 | 状态 | commit | 证据 |
|---|---|---|---|
| S16 | ✅ 动效体系 |  | （120/220/320 + 标准缓动）、NavHost 转场、tab fade、、进度补间、、跑马灯两端渐隐； 3/3；真机截图 `21-s16-dock.png`/`22-s16-player.png`，录屏 `s16-transitions.mp4`（本机无 ffmpeg，抽帧未做） |
| S17–S21 | ⏳ 待续 | — | — |

## 2. 人工门禁

- S22 FULL 红 → 不进 S23；S23 P0/P1 必须处置；S24 由人给结论。
- 共享元素/转场必须有真机录屏或抽帧证据，不接受「代码看起来对」。

## 3. 重分级触发

- 需要新增依赖、改 Pager/播放链路、改数据/schema、改版本号；
- 需要动 P0/P1 冻结 SPEC 或 token 数值；
- 设备不可用导致视觉 AC 无法验证（需人决定是否接受静态验证）。
