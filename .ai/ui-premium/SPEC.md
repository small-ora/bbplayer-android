# SPEC v1 — UI 高级感 P0（版本 v9.0）

> 级别：**L2**（跨 8+ 文件、主题 token + 多页面行为、UI 取舍、>1 小时）
> 依据：[docs/ui-premium-review.md](../../docs/ui-premium-review.md) §4 P0-1 ~ P0-6
> 状态：待实现（SPEC 冻结于本提交；实现期间不得改 AC）

## 1. 目标

在**不新增依赖、不改功能行为、不改数据/网络/播放链路**的前提下，修掉六个「可读性与精致度」硬伤，让亮/暗/纯白三套主题在静态与代码层面都达到可读、有层次、可点：

1. 播放页任意封面取色下，白色文字对比度 ≥ 4.5:1（当前实测 1.55:1）
2. 状态栏图标随播放页背景亮度切换
3. 次级文字不再靠 alpha 堆叠，实色 token 对比度 ≥ 4.5:1
4. 浅色/纯白主题卡片与页面底可区分，卡片=白面 + 12% 黑 hairline
5. 播放页 5 个弹层颜色 token 化，纯白主题不再出现深灰弹层
6. 列出的可点元素触控热区 ≥ 48dp

## 2. 可验证 AC（实现后逐条附证据）

| # | AC | 验证方式 |
|---|---|---|
| AC1 | 对 ≥6 组极端封面取色（亮黄/白/青/红/绿/黑），经 `PlayerBackdrop.washForBackground(color, scrimAlpha)` 处理后，白色文字对「合成后的背景」WCAG 对比度 ≥ 4.5:1；且滤镜输出饱和度 S ≤ 0.55、明度 V ∈ [0.08, 0.45] | `:app:testDebugUnitTest` 中 `PlayerBackdropTest` 全绿 |
| AC2 | 播放页 `GRADIENT` 模式下 scrim 绘制顺序在模糊封面**之上**（旧实现的 `.background` 挂在图片下方、渐变被图片覆盖的问题消除） | 代码检查：`PlayerScreen.kt` 中 scrim Box 作为独立层位于 `AsyncImage` 之后；`git diff` 可读 |
| AC3 | 播放页状态栏图标：背景判定为亮（纯白主题 / 高亮 wash）时用深色图标，否则用浅色图标；离开播放页恢复进入前的设置 | 代码检查 `PlayerScreen.kt` 的 `DisposableEffect`；真机验证 BLOCKED（无设备），见 ACCEPTANCE |
| AC4 | 浅色主题：`background=#F2F3F5`、`subBackground=#FFFFFF`、`popup=#FFFFFF`、`subText=#5F6368`、`text=#17181A`、`stroke=12% 黑`；`subText` 对白对比度 ≥ 6:1 | `ThemeTokensTest`（纯 Kotlin 计算 token 常量）全绿 + 截图 BLOCKED |
| AC5 | 纯白主题：`subBackground=#F4F5F6 ≠ background=#FFFFFF`，卡片可见；`HomeScreen` 的 `needHairline` 兜底分支删除 | grep `needHairline` = 0；`ThemeTokensTest` 断言两者不相等 |
| AC6 | 文本不再用 `subText.copy(alpha=…)` 表达层级：文本类用法为 0（保留非文本用途：进度轨、开关轨、禁用态）；`BbTokens.CaptionAlpha` 删除 | grep 结果分类列表（见证据）+ 编译绿 |
| AC7 | 播放页弹层使用 token（`BbExtraColors.playerSheet/playerSheetOn` 等）；`PlayerScreen.kt` 中 `Color(0xFF262626)` 出现 0 次；纯白主题弹层为浅色 | grep + 代码审查 |
| AC8 | 触控热区 ≥ 48dp：播放页顶栏收起/更多、底部 5 个功能键、进度条拖动区（≥44dp）、列表 more、分组标题操作；例外：搜索框清空键 40dp（行高 46dp 物理限制，SPEC 显式豁免） | grep `size = 32`/`size = 36` 清零 + 代码审查 |
| AC9 | 无功能回归：FULL gate 全绿（data/playback/lyrics/app 单测 + assembleDebug） | 证据日志 `evidence/final-gate.log` |
| AC10 | 范围约束：不新增依赖、不改版本号、不改数据/网络/播放行为、不 push/不合入 | `git diff --stat` + `git diff` 人工核对；`git log` 本地 |

## 3. 非目标（本轮不做，留给 P1/P2）

- 字阶 7 档重构、行高/tnum 体系（P1-1）
- 间距/圆角/图标 token 全量收敛（P1-2）
- 底部 dock 合并、导航去 label/pill（P1-3）
- 深度体系全量（卡片 veil 化、封面阴影）→ 本轮只做 hairline + 白色卡片
- 动效体系、共享元素、骨架屏、空/错误态、设置页/发现页/我的页改版、触感（P2）
- 不改 `AlbumWash` 的取色算法本身（亮度守卫在 `PlayerBackdrop` 统一处理）

## 4. 风险与门禁

| 风险 | 缓解 |
|---|---|
| 主题中性色改动影响全局观感 | 只改中性 token（bg/surface/subText/stroke），保留 accent/动态取色/播放页 wash 逻辑；改前改后截图对比（BLOCKED 部分用模拟预览） |
| 动态取色（Monet）模式下 `withLightNeutrals` 覆盖 subText/stroke 可能与 Monet 色相不协调 | 中性 token 固定为中性灰阶，与任意 accent 兼容；纯白/浅色/深色三套一致 |
| 无真机/模拟器，视觉类 AC 无法真机验证 | 明确标 BLOCKED；提供单元测试（对比度/亮度守卫）+ 代码检查 + 按同一算法生成的模拟预览图（标注「模拟」） |
| 播放页 blur 性能 | 维持原有 `blur(90.dp)` 与层级数量，不新增全屏模糊；仅调整 scrim 层序 |
| 触控热区放大导致布局位移 | 只在可点容器上扩 `size`/`sizeIn`，图标视觉尺寸单独保持（IconAction 图标 = 热区 50%） |

## 5. 契约变更

- 新增 `ui/theme/PlayerBackdrop.kt`（纯 Kotlin 颜色数学，无 android 依赖，可单测）
- 新增 `ui/theme/ThemeTokens.kt`（中性色常量，供 `BbTheme` 与测试共用）
- `BbExtraColors` 增加 `pureWhite`、`playerSheet`、`playerSheetOn`、`playerSheetOnSecondary`
- `BbTokens.CaptionAlpha` 删除
- 不改任何 repository / controller / data 模型
