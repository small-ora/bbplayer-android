# SPEC v1 — UI 高级感 P2（v9.2）

> 级别：**L2**（跨 20+ 文件、动效/共享元素/多页面改版、含设计取舍、>1 天）
> 依据：[docs/ui-premium-review.md](../../docs/ui-premium-review.md) §4 P2-1 ~ P2-6 + `.ai/ui-premium/PLAN.md` §3（v9.2 S16–S21）
> 状态：SPEC 冻结于本提交；实现期间不得改 AC
> 基线：`feat/ui-premium-p0`（v0.9.0，已发布）+ `feat/ui-premium-p1`（v9.1，实现开始时 rebase 到其最新提交）

## 1. 目标

在 v9.0（可读性）与 v9.1（系统感）之上补齐「生命力与编辑感」，不改数据/网络/播放行为、不新增依赖：

1. **动效体系**：统一 120/220/320ms + FastOutSlowIn；页面转场、tab 切换、播放态切换、列表增删、进度动画、跑马灯边缘渐隐。
2. **封面共享元素**：迷你播放器封面 → 全屏播放器封面连续 morph（`SharedTransitionLayout`）。
3. **发现页 hero**：新增「继续收听」大卡；模块高度/间距分级，结束「四个板块同节奏」。
4. **设置页**：accent 分组标签 + 前导图标 + 56dp 行高 + 危险项 + 版本收尾卡。
5. **我的页**：身份 + 收听数据合并 hero；热力图空格可见。
6. **触感与图标**：关键交互触感三件套；图标单一族、尺寸 token 化、选中态 fill/outline 成对。

## 2. 可验证 AC

| # | AC | 验证方式 |
|---|---|---|
| AC1 | `ui/theme/Motion.kt` 提供 `BbMotion.Fast=120/Base=220/Slow=320` + `BbEasing`；NavHost 显式配置转场（根 tab `fadeThrough`、二级页横向 slide+淡入），无「未配置 → 库默认 700ms 淡入」；tab 切换 `Crossfade(180–220ms)`；播放/暂停图标 `AnimatedContent`；进度 `animateFloatAsState`；队列/歌单增删 `animateItem`；迷你条跑马灯 `edgeFade=true` 或两端渐变 | grep + 代码审查 + 真机 `screenrecord` 抽帧（设备已验证可用） |
| AC2 | mini→播放器：`SharedTransitionLayout` + `sharedElement` 让封面从 40dp 连续 morph 到全屏（无跨页闪烁/错位）；返回时反向；快速开关 10 次无崩溃 | 代码审查 + 真机 `screenrecord` 抽帧 + 手动 10 次 |
| AC3 | 发现页：历史非空时顶部出现「继续收听」16:9 hero（封面/模糊底 + 渐变 + 22sp 标题 + 48dp 播放键），点击可播放；模块高度分级（hero / rail / list）与间距 24/32 交替；历史为空时回退现有布局 | 真机截图（浅/深/纯白）+ 代码审查 |
| AC4 | 设置页：分组标签 accent 12sp/Medium；行高 ≥56dp、24dp 前导图标（highlight 12% 圆底）；危险项（退出登录/删除下载）用 error 色；底部版本卡；所有原设置项功能不变 | 真机截图 + 逐组点按回归 |
| AC5 | 我的页：身份 + 累计收听/播放次数合并为 hero 卡（数字 22sp/700）；热力图空格有 1dp 描边可见；下方入口行行为不变 | 真机截图（空数据/有数据各一次）+ 代码审查 |
| AC6 | 触感：收藏切换 `ToggleOn/ToggleOff`、加歌单/下载成功 `Confirm`、失败 `Reject`；播放/暂停与滚动无触感；不新增偏好设置（记录为非目标） | 代码审查（`performHapticFeedback` 调用点清单）+ 真机手感确认（主观项） |
| AC7 | 图标：单一族（Material Rounded，选中态成对 `*` / `*Outlined`）；尺寸只取 `BbIconSize` 16/20/24；无 `Text("✓")` 之类字形图标；底部 dock 选中/未选中 fill 状态正确 | grep + 真机截图 |
| AC8 | 无回归：FULL gate 全绿（data/playback/lyrics/app 单测 + assembleDebug）；不新增依赖、不改版本号、不改数据/网络/播放行为 | `evidence/final-gate.log` + `git diff --stat` |

## 3. 非目标（本轮不做）

- 不改 P0/P1 已冻结的 SPEC 与 token 数值。
- 不做新功能（下载/评论/歌词逻辑不动）；不改路由与数据层。
- 不新增依赖（不加 shimmer / 图标库 / 动画库）；图标继续用 `material-icons-extended`。
- 不新增「关闭触感」设置项（避免动 preferences/schema）。
- 不做播放页 pager 结构重写（共享元素只包封面，不动 Pager）。

## 4. 风险与门禁

| 风险 | 缓解 |
|---|---|
| `SharedTransitionLayout` 与现有全屏 overlay / Pager 结构叠加 | 只把封面包进 shared element；单独 commit，可单独 revert |
| 页面转场改动导致返回手势/深链异常 | 根 tab 与二级页分别配置；真机抽帧验证 10 次进出 |
| 动效导致掉帧 | 只用 transform/alpha；不引入全屏 blur；`screenrecord` 抽帧检查 |
| 设置/我的页改版碰功能入口 | 只改视觉层级与行结构，不改回调；逐项点按回归 |
| 与 v9.1 并行改同一文件 | **v9.2 实现从 v9.1 S14/S15 提交后开始**，先 rebase；同一文件只由 Lead 改 |

## 5. 契约变更

- 新增 `ui/theme/Motion.kt`（时长/缓动常量，纯 Kotlin 可单测）
- `AppRoot.kt` 增加转场与 shared element 容器；`MiniPlayer` 暴露 cover shared key；`PlayerScreen` cover 节点接入
- 新增 `HomeScreen` 的 `ContinueListeningHero`；`SettingsScreen`/`ProfileScreen` 重排（不改回调）
- 不改 repository/controller/data 模型/路由/版本号
