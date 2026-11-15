# REVIEW — BBPlayer 安卓原生版 v1

**审查依据（冻结）**：`git show c0fbd1c:.ai/spec-android-native/SPEC.md`（审查期间 SPEC 未被改动：`git diff c0fbd1c..HEAD -- SPEC.md` 为空）
**审查对象**：`git diff c0fbd1c..HEAD`（99 文件 / +11013 行，含实现、测试、文档）

## 审查者记录

| 审查者 | 类型 | 是否看过实现过程 | 审查范围 | 状态 |
| --- | --- | --- | --- | --- |
| fresh subagent（`5b1e9f72…`） | 独立子代理 | 否 | 全量 diff + SPEC 逐条 | **超时未交付**（运行 >50 分钟，未产出报告；流程缺口，如实记录） |
| lyrics-dev | 独立同伴（fresh teammate，未写 `app/**` 与 `data/**`、`playback/**`） | 部分（熟悉 specs/contracts，未参与 app UI 实现） | `app/**` UI 正确性（12 条）、歌词模块 | 已交付：`.ai/recon/ui-review-lyrics-dev.md` |
| Lead | 自审（非独立） | 是 | 全量集成、data/playback 接线、真机回归 | 已执行 |

> 按工作流要求，L2 应使用 fresh reviewer；本次 fresh subagent 未在时限内交付，故独立审查由「未参与实现的同伴 agent + Lead 自审」组合完成，并在验收包首屏明确标注。**建议人工验收时自行通读关键 diff**（`app/di/RealAppGraph.kt`、`app/ui/AppRoot.kt`、`playback/PlaybackControllerImpl.kt`、`data/BiliMusicRepository.kt`）。

## Findings 与处置

| # | 级别 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| 1 | P0 | `ui/screens/library/LibraryScreen.kt:134` | 最近列表 LazyColumn key 用 `track.key+duration`，同一首歌重复播放 → key 冲突 → Compose 抛异常崩溃 | **已修**：key 改为 `track.key+"@"+playedAt`，并改用按索引渲染；真机打开「最近」无崩溃（`shots/22-recent.png`） |
| 2 | P1 | `ui/screens/search/SearchScreen.kt:67/133` | 重试/同词重搜时 LaunchedEffect key 不变 → 永远 loading | **已修**：新增 `searchNonce` 参与 key，重试/重搜均 `++` |
| 3 | P1 | `ui/screens/player/LyricsPanel.kt:61-74` | `animateScrollToItem` 触发 isScrollInProgress，被误判为用户滚动，自动滚动失效 | **已修**：`programmaticScroll` 标志区分程序滚动；用户滚动后 2.5s 恢复跟随 |
| 4 | P1 | `ui/screens/player/PlayerScreen.kt:115` | 歌词偏移只存内存、不持久化（AC D4 不成立） | **已修**：±0.1s 直接写 `PreferencesRepository.setLyricsOffsetMs`；真机重进仍显示 200ms（`shots/17`） |
| 5 | P1 | `ui/screens/player/PlayerScreen.kt:283` | 「刷新歌词」调用 `clearCache()` 清空全部歌曲歌词（含手动粘贴） | **已修**：改为「搜索歌词」候选对话框（QQ/网易云候选，选择后写缓存） |
| 6 | P1 | `ui/screens/player/PlayerScreen.kt:117` | 歌词无 loading/error 三态，失败只显示「暂无歌词」 | **已修**：loading / error（重试、手动搜索）/ 空态三态；真机截图 `shots/17` |
| 7 | P1 | `ui/AppRoot.kt:84` 等多处 | `rememberCoroutineScope().launch` 裸调用，异常会崩溃 | **已修**：新增 `core/SafeLaunch.kt`，UI 侧全部改 `safeLaunch`（Setting/Player/Library/AppRoot 等）；`PlaybackHistoryTracker` 保留长生命周期 collect |
| 8 | P1 | `BbPlayerApp.kt:18` | 仍固定 DemoGraph，真实后端未接线 | **已修**：`RealAppGraph` 接线 data/playback/lyrics；删除 DemoGraph；真机真实数据验证 |
| 9 | P1 | `Search/Home/Library/PlaylistDetail` | 用 `tracks.indexOf(track)` 反查播放下标，重复曲目会播错 | **已修**：全部改为 `items(count=…)` + 索引渲染 |
| 10 | P2 | `ui/components/SeekLine.kt:92` | 把 px 当 dp 用于滑块偏移 | **已修**：`offset { IntOffset(px) }` |
| 11 | P2 | `ui/theme/AlbumWash.kt:40` | 全尺寸解码封面做取色（内存/耗时） | **已修**：`inSampleSize=4` 降采样 + 饱和度增强 + 失败兜底 |
| 12 | P2 | `ui/screens/player/PlayerScreen.kt:129` | 每 tick copy Lyrics 触发整列重组 | **已修**：`remember(lyrics, prefsOffset)` 记忆化 |
| 13 | P2（自审） | 断点续播 | 首次接线遗漏 restore 调用（真机复现无迷你条） | **已修**：`AppRoot` LaunchedEffect 调用 `restore()`；真机 force-stop 后重启恢复曲目与进度（`shots/13-restore.png`） |
| 14 | P2（自审） | Release 构建 | R8 对 Kotlin 2.4 元数据告警（不影响产物） | **记录**：`assembleRelease` exit 0，产出 4.1MB 未签名 APK；告警属 R8 版本支持范围问题 |

## 未被处置（明确记录，非阻塞）

- WBI 签名未实现（匿名只读接口实测可用；风控收紧时需补）。
- 搜索/榜单 `cid=0` 时播放依赖 pagelist 兜底（多 P 视频默认 P1）。
- 定时关闭到点暂停、403 重试、断网错误态：单测 + 代码路径覆盖，未做真机人为构造验证。
- 歌词自动滚动修复后未做长时真机观察（静态截图可证高亮与居中）。

## 结论

**未发现未处置的 P0 问题。** 独立同伴审查发现的 P0×1、P1×8 全部修复，并重跑全量门禁（165 用例全绿 + assembleDebug/Release 成功）+ 真机回归（搜索/播放/通知/歌词/歌单/收藏/最近/主题/续播）。fresh subagent 全量审查超时未交付，已如实记录为流程缺口。

---

## 补充：fresh subagent 最终交付（于 Lead 定稿后到达）

独立审查者（fresh subagent，未看实现过程）最终提交报告，findings 与 Lead 处置：

| # | 级别 | 位置 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| 15 | **P0** | `playback/TrackUri.kt:59` | 搜索/BV 结果 `cid=0` 被 URI 解析器拒绝 → ResolvingDataSource 不解析 → 搜索→播放 100% 失败（榜单因帶 cid 可播）；且 UI 无错误提示 | **已修**：放行 `cid=0`（data 层 pagelist 兜底）。真机验证：搜索 `BV1auGwzMEs1` → 点结果 → 播放在跑（00:06/03:00，`shots/23-search-play.png`）；`TrackUriTest` 增补 cid=0 用例（原「拒绝 cid=0」断言按新契约修改，已在验收包注明） |
| 16 | P1 | `PlaybackControllerImpl.kt:461` | 设置里的音质没有进入播放链路（恒 192K，徽标误显示） | **已修**：`AppRoot.playTracks()` 播放前读取 `audioQuality` 并写入 Track.qualityId |
| 17 | P1 | `SearchScreen.kt:71` | 未走 `resolveInput`，AV/URL/b23 被当关键词 | **已修**：先 `resolveInput`，失败再关键词搜索 |
| 18 | P1 | `PlayerScreen.kt:90` | 播放错误 `errorMessage` 不展示，无重试入口 | **已修**：播放页顶部错误条 + 重试 |
| 19 | P1 | `LibraryScreen.kt:164` / `PlaylistDetail` | 歌单缺重命名、单曲移除 | **已修**：歌单行「重命名」弹窗；歌单详情行末移除按钮 |
| 20 | P1 | `PlaybackHistoryTracker.kt:30` | 只在切歌时记历史，最后一首丢失、秒切也计入 | **已修**：暂停/播完/出错时也落库；按实际播放时长累计 |
| 21 | P2 | 许可/README | SaltUI 2.8.6 许可表述不一致；README 称 MIT 但缺 LICENSE | **已修**：按 Maven POM 更正为 Apache-2.0（并注明 2.8.6 部分源文件头残留 LGPL-2.1 的歧义，供人裁定）；新增 MIT `LICENSE` |
| 22 | P2 | `:app` 无单元测试；AC A2 只统计模块测试 | **记录**：A2 已在验收包按模块口径写明（165 用例来自 core 之外三个模块）；UI 层无单测属已知缺口 |

SPEC 变更检查（reviewer 复核）：`git diff c0fbd1c..HEAD -- .ai/spec-android-native/SPEC.md` 为空，无未说明变更 ✅

**结论更新**：独立审查共发现 P0×2（历史列表重复 key、cid=0 播放失败，均已修复并真机回归）、P1×13（全部修复）、P2 若干（能修的已修，许可/测试口径已在验收包如实说明）。未发现未处置的 P0/P1 阻塞项。
