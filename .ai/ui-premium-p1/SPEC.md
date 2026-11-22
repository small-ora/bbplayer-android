# SPEC v1 — UI 高级感 P1（v9.1）

> 级别：**L2**（跨 25+ 文件、主题 token + 底部导航结构调整、多页面视觉行为、含设计取舍、>1 小时）
> 依据：`docs/ui-premium-review.md` §4 P1-1 ~ P1-5 + `.ai/ui-premium/PLAN.md` §2（v9.1 S11–S15）
> 状态：SPEC 冻结于本提交；实现期间不得改 AC（AC 变更 = SPEC 升版本 + 人确认）
> 基线：`BASE = 93c8a07`（`feat/ui-premium-p0` HEAD，v9.0 代码已提交、待人工验收）

## 1. 目标

在不新增依赖、不改数据/网络/播放行为的前提下，把 v9.0 修好的「可读性」升级为「系统感」，落地 5 个可检查的系统层：

1. **字阶**：7 档字号/行高/字重单一事实源，页面内不再散落 `fontSize = N.sp`；时间/计数等宽。
2. **间距/圆角/图标**：4/8/12/16/20/24/32/48 间距、8/12/16/28/full 圆角、16/20/24 图标 + 48 热区 token 化。
3. **底部 dock**：迷你行 + icon-only 导航合并进一个 28dp 圆角容器，去掉 M3 pill 与 label。
4. **深度体系**：卡片「白面/veil + hairline、不投影」；封面 24dp 环境阴影 + 同图光晕；dock 16dp 软阴影 + 顶边 hairline。
5. **状态**：骨架屏 + 图片占位/淡入/错误占位 + 空态/错误态重设计（首屏不再全屏转圈）。

## 2. 可验证 AC（实现后逐条附证据）

| # | AC | 验证方式 |
|---|---|---|
| AC1 | `ui/theme/` 新增 `BbType` 七档：Display 28/34 Bold、Title 22/28 SemiBold、Section 18/24 SemiBold、Body 15/22 Regular、Label 13/18 Medium、Caption 12/17 Regular、Micro 11/16 Medium；页面/组件层 `fontSize = <数字>.sp` 出现 0 次（仅 `ui/theme/` 与 `ui/share/ShareCard.kt` 的画布 Paint 例外） | `grep -rn 'fontSize *= *[0-9.]*\.sp' app/src/main/java/com/bbplayer/android/ui --include=*.kt \| grep -v '/theme/' \| grep -v 'ShareCard.kt'` 输出为空；`:app:testDebugUnitTest` 中 `BbTypeTest` 全绿（7 档 size/lineHeight 断言 + 行高 > 字号） |
| AC2 | 时间/时长/计数文本使用 `fontFeatureSettings = "tnum"`：`SeekLine` 当前/总时长、播放页时间、`TrackRow` 时长、`HistoryScreen` 时长、`ProfileScreen` 统计数字、`HomeScreen` 卡片时长至少覆盖；`BbType` 提供 tnum 变体 | `grep -rn 'tnum' app/src/main/java` ≥ 6 处且落在上述文件；代码审查 |
| AC3 | 间距/圆角/图标 token 落 theme：`BbSpacing`(4/8/12/16/20/24/32/48)、`BbRadius`(8/12/16/28/full)、`BbIconSize`(16/20/24)+48 热区；删除 6/10/14/18/22/26 这些「中间值」在组件/页面层的内边距用途（描边 0.5/1dp、进度条 2dp、特殊图形尺寸等结构值可豁免并列入例外清单）；`IconAction` 的 `size * 0.55f` 类魔法比例清零 | grep 报告 + 例外清单写入 `evidence/`；代码审查 |
| AC4 | 底部 dock：手机端根 Tab 单一容器（上下两行）——上 64dp 迷你行、下 56dp icon-only 导航，容器 28dp 圆角；无 `NavigationBar` / `NavigationBarItem` / label / 选中 pill；选中 icon `text` 100%、未选中 `subText` 45%；`navigationBarsPadding` 在容器内；`BbTokens.RootContentBottomPadding` 与实际 dock 高度一致 | grep `NavigationBar`（`components/BottomNavBar.kt` 之外）= 0；代码审查 + 截图像素检查（设备 BLOCKED 时用模拟图） |
| AC5 | 深度体系：卡片=白面/veil + 1dp hairline、无阴影；封面=24dp 环境阴影 + 同图光晕（层级低于内容）；dock=16dp 软阴影 + 1dp 顶边 hairline；全应用 `shadow(` 只允许出现在封面/dock（Sheet 自带阴影除外） | `grep -rn 'shadow(' app/src/main/java/com/bbplayer/android/ui` 清单白名单核对；代码审查 |
| AC6 | 状态：首屏加载用骨架屏（行高 64dp、12dp 圆角、alpha 呼吸），列表页不再首屏全屏 `CircularProgressIndicator`；`CoverImage` 的 `AsyncImage` 配置 placeholder/error/crossfade(200ms)；空态 = 120dp 单色符号 + 17sp/600 标题 + 13sp 说明 + 48dp 主按钮；错误态为内联卡片（不打断布局） | grep + 代码审查；`States.kt`/`CoverImage.kt` 检查；设备截图 BLOCKED 时用模拟图 |
| AC7 | 无功能回归：FULL gate 全绿（data/playback/lyrics/app 单测 + assembleDebug）；不新增依赖、不改版本号、不改数据/网络/播放行为、不 push 不合入 | `evidence/final-gate.log`；`git diff --stat 93c8a07..HEAD` 人工核对 |

## 3. 非目标（本轮不做）

- P2 全部：动效 token / 页面转场 / 共享元素 / 发现页 hero / 设置页改版 / 我的页 hero / 触感 / 图标库替换。
- 不改 P0 已冻结的 SPEC 与 ACCEPTANCE，不动 `feat/ui-premium-p0` 分支。
- 不改主题中性色数值（P0 冻结），只消费。
- 不替换图标库（Material Icons Rounded 保留，仅统一尺寸/选中态）。
- 不改 `AlbumWash` / `PlayerBackdrop` 的取色与亮度算法。

## 4. 风险与门禁

| 风险 | 缓解 |
|---|---|
| P0 尚未人工验收，P1 基于其 HEAD；若 P0 打回改动共享文件 | P1 全部是本地提交，可 `git rebase feat/ui-premium-p0`；验收包写明依赖关系 |
| 字阶/间距批量替换导致版式位移（尤其短屏/横屏固定高度行） | 一主题一 commit；每步 QUICK 绿；保留固定行高（TrackRow 64dp、Settings 56dp 等）不随字阶膨胀 |
| 三主题视觉判断无真机 | 先做静态/单测/模拟图；设备位被 P0 会话占用时不并发截图，标 BLOCKED 并写清 |
| dock 合并改动 AppRoot 层级，回归 mini 播放器手势/导航切换 | 保持 `MiniPlayer` 手势与 `switchTab` 逻辑不变；仅替换容器与视觉；单独 commit 便于回滚 |
| 骨架屏引入 `Shimmer` 动画/新依赖 | 不新增依赖，用 `rememberInfiniteTransition` + `alpha` 呼吸 |
| 深度体系全量阴影影响性能 | 阴影只给封面（`shadow` 绘制成本可控）与 dock（单容器）；卡片保持无阴影 |

## 5. 契约变更

- 新增 `ui/theme/Typography.kt`（`BbType` 7 档 + tnum 变体，纯 Compose TextStyle，无业务依赖）
- `ui/theme/DesignTokens.kt` 增加 `BbSpacing` / `BbRadius` / `BbIconSize`；`BbTokens` 的底部 chrome 尺寸随 dock 调整
- `ui/components/BottomNavBar.kt` 重写为 dock 导航段（内部 API，仅 `AppRoot` 调用）
- `ui/components/MiniPlayer.kt` 增加「嵌入 dock」模式（去独立悬浮卡片阴影/圆角/外边距）
- 不新增依赖、不改任何 repository / controller / data 模型 / 路由

## 6. 设计取舍（实现前先记录，验收时供人复核）

1. **字阶映射表**（页面现有字面量 → 7 档；消除 6/10/14/16/17/22/24/26 等中间值）：
   - 9/10/11 → Micro；12 → Caption；13 → Label；14/15 → Body；
   - 16/17/18 → Section；20/24 → Title；26/28+ → Display（详情页大标题）。
2. **未选中导航图标 `subText 45%`**：这是 P1-3 的既定设计（导航图标非正文，不违反 P0「正文不用 alpha」）；正文仍用 P0 实色 token。
3. **保留 P0 的三套中性色**：P1 只增加类型/间距/深度，不改色值。
4. **骨架屏范围**：列表首屏（Home 发现流、Library、History、Downloads）与播放页封面加载；不重做全部 loading。
5. **封面光晕**：用封面取色 + `radialGradient` 作为封面后方的低成本光晕，不引入 Blur（避免性能回归）。
