# 验收包 v7 — 播放页三页横滑（封面 / 歌词 / 评论），歌词偏移统一到设置

**级别**：L2（播放页 UI/手势重构）· 分支：`feat/player-pager`（基于 main `0d502f8` v0.6.0）
**状态**：**已实现、构建/单测通过、真机验证通过、独立审查 PASS（4 条 P2 已修复）；未合入 main、未发布（等你授权）**

## 1. 用户需求

> 进入播放页默认显示封面图，然后可以左滑切换到歌词界面，然后再左滑切换到评论界面。歌词界面不要有调整按钮，就是展示用的。如果要调整，就在菜单的设置里添加歌词的偏移调整。

## 2. 交付内容

| 区域 | 内容 |
|---|---|
| 播放页结构 | `HorizontalPager` 三页：0=封面（默认）、1=歌词、2=评论；左右滑切换；顶部栏保持「收起 / 正在播放·UP主 / 更多」，其下加 3 点页码指示 |
| 歌词页 | `LyricsPanel` 纯展示：高亮/逐字/翻译/点行 seek 保留；**删除** ±0.1s 与「偏移 N ms」调整控件与逐曲偏移状态；底部保留迷你播放控制（上一首/播放/下一首） |
| 评论页 | 复用 `CommentsScreen(embedded=true)` 内嵌为不透明圆角卡片：评论分页/楼中楼/加载更多/查看回复全部保留；去掉返回栏，改为「评论 · 共 N 条」标题 |
| 入口联动 | 底部「歌词」按钮跳页 1；更多菜单「查看评论」跳页 2（不再离开播放页）；评论独立路由保留但不再从播放页进入 |
| 歌词偏移 | 唯一入口：设置 → 歌词 → 歌词偏移（v6 已有；±100ms，范围 -2000~2000ms，持久化） |

## 3. AC 对照与证据

| # | AC | 结果 | 证据 |
|---|---|---|---|
| AC1 | 进入播放页默认封面页 | PASS | 真机打开播放页 dump：`正在播放/上传者/标题/HQ/时间`；截图 `.ai/evidence/v7/02-player-cover.png` |
| AC2 | 左滑 封面→歌词→评论，右滑反向，内容正确 | PASS | 3 次滑动截图：`02-player-cover.png`、`03-player-lyrics.png`、`04-player-comments.png`；右滑两次回封面 dump 有「收藏/暂停」desc；评论页 dump「评论 / 共 35 条」+ 评论内容 |
| AC3 | 歌词页纯展示，无 ±0.1s/偏移调整按钮；歌词功能正常 | PASS | 歌词页 dump 文本无「偏移/0.1s/调整」；有歌词行、MiniTransport（上一首/播放/下一首）；截图 `03-player-lyrics.png`、`05-lyrics-via-button.png`；点行 seek 沿用原 `LyricsPanel(onSeekTo)` 未改 |
| AC4 | 设置里可调歌词偏移，持久化 | PASS | 设置→歌词→歌词偏移：0 ms → 点「+」→「+100 ms」→「−」回 0（`09/10-settings-*.png`）；偏好 `lyricsOffsetMs` 未改，播放页读取 `prefsOffset` |
| AC5 | 评论页可读、分页/回复可用 | PASS | dump：作者/内容/日期/点赞数/「查看回复 (N)」；垂直滚动后列表内容变化（`08-comments-scrolled.png`）；白色圆角卡片在浅色主题下可读（`04-player-comments.png`） |
| AC6 | 回归：播放控制/进度拖动不误翻页/收藏/倍速/定时/队列/更多/桌面歌词 | PASS | seek 横幅拖动：页面仍为封面、时间 03:58→09:50（`07-seek-drag-no-page-change.png`）；「歌词」按钮跳页、「查看评论」跳页、播放/暂停/上下一首可用；`logcat` FATAL=0；桌面歌词隐藏逻辑改为 pager 第 1 页可见时隐藏 |
| AC7 | 构建 + 全模块单测 | PASS | `./gradlew :app:assembleDebug :app:testDebugUnitTest :data:testDebugUnitTest :playback:testDebugUnitTest :lyrics:testDebugUnitTest` → exit 0（615 tests / 0 fail / 0 skip）`.ai/evidence/v7/gate.log` |

## 4. 独立审查

- 审查者：fresh subagent `b093caa9-39dd-4edd-a40e-1ba38945cfab`（未参与实现、只读），依据 SPEC `ec63a1a`，对象 `0d502f8..ba78efe`。
- 结论：**REVIEW_V7_RESULT: PASS**（无 P0/P1；4 条 P2 建议，均已修复并真机复验，见 §7）。

## 5. 风险与回滚

- 回滚：`git revert ba78efe`（或丢弃 `feat/player-pager` 分支）；未改数据层/偏好键，回退无数据迁移。
- 逐曲歌词偏移（v2）的播放页调整入口已移除；**旧值仍会读取叠加生效**（不静默丢弃），但当前没有 UI 可修改/清除逐曲偏移，全局偏移在设置里调。
- 评论页内嵌后，播放页外已无入口进入独立评论路由（路由保留，未删除）。
- pager 手势与 `SeekLine` 横向拖拽：真机验证 seek 生效且不误翻页；`LyricsPanel` 垂直滚动正常。

## 6. 我替你做的判断与假设（可否决）

1. 歌词页保留迷你播放控制（上一首/播放/下一首），只去掉「调整按钮」（按「展示用」理解为去掉偏移调节）。
2. 评论页做成不透明圆角卡片（保证浅/深色可读），而非透在封面渐变上。
3. 顶部加 3 点页码指示（可发现性），不额外做引导浮层。
4. 更多菜单「查看评论」保留为跳页快捷方式（不是重复配置）。
5. 未自动合入 main、未发布新版本（等你授权；如需发布建议 v0.7.0）。

## 7. 独立审查结论与处置

审查结论 **PASS**（无 P0/P1）。4 条 P2 处置如下（提交 `fix(player): 独立审查 P2 修复…`）：

| # | 级别 | 问题 | 处置 | 复验 |
|---|---|---|---|---|
| 1 | P2 | 评论页状态随翻页被回收（加载更多页/楼中楼/阅读位置丢失）；同一播放页内切歌时 `LazyListState` 不重置 | **已修**：`beyondViewportPageCount = 2`（三页保持组合，状态不丢）+ `CommentsScreen` 的 `LazyListState` 用 `remember(bvid)` 键控（切歌重置） | 真机：评论页滚动 → 滑回封面 → 再滑回评论，首条可见评论与滚动位置一致（`12-comments-state-retained.png`） |
| 2 | P2 | `beyondViewportPageCount=1` 时滑到歌词页就预加载评论（无谓请求） | **已修**：`CommentsScreen(active=...)` 门控，只有 `currentPage == 2` 才 `fetchComments` | 真机：歌词页 dump 无任何评论内容；封面页 dump 无歌词/评论文本（离屏页不进入 dump，AC1 断言仍成立） |
| 3 | P2 | 页码点写死白色，「纯白（墨水屏）」主题下不可见 | **已修**：页码点与顶部栏（收起/正在播放/UP主/更多）改用 `onPlayerColor`（纯白主题自动转深色） | 真机切「播放器背景 = 纯白（墨水屏）」：页码点/顶栏为深色可见（`13-pure-white-player.png`），已恢复原背景 |
| 4 | P2 | 死代码（不可达评论路由）+ 旧「逐曲偏移」被静默丢弃 | **已修**：删除不可达的 `Routes.COMMENTS`/`comments()`/独立评论路由（评论页仅内嵌）；**保留读取** v2 逐曲偏移并叠加到 `effectiveLyrics`（无调整按钮，不会静默丢用户既有微调），验收包/README 注明 | 代码走查 + 编译/门禁 |

- 审查者已核对无问题：三页顺序与默认页、pager 布局、歌词面板参数与 seek 一致性、桌面歌词可见性恢复、embedded 评论复用逻辑、SeekLine 与 pager 手势优先级、各入口 index、无测试/CI 削弱、无新增依赖。
- 修复后门禁：`assembleDebug + app/data/playback/lyrics 单测` → exit 0（615 tests / 0 fail / 0 skip；`.ai/evidence/v7/gate.log`）。

## 8. 证据索引

- 门禁：`.ai/evidence/v7/gate.log`
- 截图：`02-player-cover.png`、`03-player-lyrics.png`、`04-player-comments.png`、`05-lyrics-via-button.png`、`06-comments-via-menu.png`、`07-seek-drag-no-page-change.png`、`08-comments-scrolled.png`、`09-settings-lyrics-offset.png`、`10-settings-offset-plus.png`、`11-cover-after-p2.png`、`12-comments-state-retained.png`、`13-pure-white-player.png`
- SPEC/PLAN：`.ai/spec-android-native/SPEC-v7.md`、`PLAN-v7.md`
