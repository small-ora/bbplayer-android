# App UI 只读审查（reviewer: lyrics-dev，2026-11-15）

范围：`app/src/main/java/com/bbplayer/android/` 全部 UI/DI 代码（`ui/AppRoot.kt`、`ui/screens/**`、`ui/components/**`、`ui/theme/**`、`di/DemoGraph.kt`、`BbPlayerApp.kt`、`MainActivity.kt`，共 3597 行）。
方式：只读通读 + 交叉核对 `data/src/.../HistoryStats.kt`、`playback/` 的调用约定；未修改任何 app 文件，未跑 adb。
判据：只报会导致运行期崩溃 / 状态错误 / 数据丢失 / Compose 误用的问题；不报风格与视觉偏好。

## 汇总

- **P0：1 条（崩溃，必现路径）**
- **P1：8 条**（状态机错误 ×2、数据丢失 ×2、歌词功能失效 ×1、崩溃风险 ×1、集成阻塞 ×1、错误播放目标 ×1）
- **P2：3 条**

---

## P0

`[P0] app/src/main/java/com/bbplayer/android/ui/screens/library/LibraryScreen.kt:134 — 「最近」列表的 LazyColumn key 为 it.key + it.durationMs，同一首歌播放两次即产生重复 key，Compose 抛 IllegalArgumentException("Key was already used") 直接崩溃 — 真实历史仓库 data/src/main/java/com/bbplayer/android/data/HistoryStats.kt:23 的 sortedRecent 只排序+take、不去重，JsonHistoryRepository.recordPlay 每次播放追加一条，所以「播放同一首歌两次 → 打开 音乐库 → 最近」是必现崩溃；且 key 无分隔符，理论上不同歌也会碰撞 — 建议 key 改为 it.track.key + "@" + it.playedAt（或渲染前 distinctBy { it.track.key }，二者取一，保持 key 与列表语义一致），并把 :138 的 tracks.indexOf(track) 改为 itemsIndexed 的显式 index。`

## P1

`[P1] app/src/main/java/com/bbplayer/android/ui/screens/search/SearchScreen.kt:67（重试入口 :133）— 重试/同词再搜会永久卡在“加载中”：onRetry = { submit(submittedQuery) } 只把 loading 置 true、error 置 null，但 submittedQuery 值没变，key 为 submittedQuery 的 LaunchedEffect 不会重启，loading 永远为 true — 行为错误（重试按钮、同一关键词再搜一次都失效）— 建议引入自增 requestId（submit 里 requestId++，LaunchedEffect(submittedQuery, requestId) 驱动），失败分支负责收敛 loading。`

`[P1] app/src/main/java/com/bbplayer/android/ui/screens/player/LyricsPanel.kt:61-74 — 自动滚动被“用户滚动”状态误伤：animateScrollToItem 自身会把 listState.isScrollInProgress 置 true，LaunchedEffect(isScrollInProgress) 立即把 userScrolling 置 true，再延迟 2500ms 才复位；期间 activeIndex 变化被 !userScrolling 直接跳过（歌词通常 2-4s 一行），表现为歌词不跟随/滞后 2.5s，且用户手动滚停后也不会按上游行为在 2s 后回中 — 建议区分程序滚动与手势滚动（例如程序滚动前后维护 autoScrolling 标志，或用 snapshotFlow 观察 scroll 且在 autoScrolling 时不置位），并把「回中」放在 userScrolling 复位时触发。`

`[P1] app/src/main/java/com/bbplayer/android/ui/screens/player/PlayerScreen.kt:115,202-215 — 播放页歌词偏移只写本地 localOffset（remember(track.key)），从不调用 prefs.setLyricsOffsetMs；退出播放页/切歌/进程重启后偏移归零，设置页偏移与播放页 ±0.1s 调整互不同步（两处相加显示）— 状态未持久化，AC D4「偏移 ±0.5s 调整生效并持久化」不成立 — 建议以 prefs.lyricsOffsetMs 为唯一真值，±0.1s 直接 setLyricsOffsetMs；若需拖动预览再用临时态。`

`[P1] app/src/main/java/com/bbplayer/android/ui/screens/player/PlayerScreen.kt:283-286 — “刷新歌词”调用 graph.lyricsRepository.clearCache()，会把所有曲目的在线缓存与手动粘贴歌词（saveManual 同目录）一起清空；刷新一首歌导致全部手动歌词丢失 — 数据丢失 + 语义错误 — 建议仓库层提供 invalidate(trackKey)/forceRefresh(track)，UI 只失效当前 track.key，或在清空前明确提示。`

`[P1] app/src/main/java/com/bbplayer/android/ui/screens/player/PlayerScreen.kt:117-124（另 :296-302）— 歌词加载无三态：lyricsLoading 定义后从未被 UI 使用；getCached/fetch 失败都只落到 lyrics=null，界面直接显示「暂无歌词」，无法区分加载中/无歌词/网络失败，也没有 D2 要求的重试与手动粘贴入口；更严重的是手动粘贴保存后，早先发出的 LaunchedEffect 请求仍可能在稍后完成并把 lyrics 覆盖回在线结果（粘贴丢失）— 建议 lyrics 改为 sealed 状态（Loading/Empty/Error/Data），fetch 用 Job 保存并在 saveManual 成功后取消，Error 态展示重试+粘贴入口。`

`[P1] app/src/main/java/com/bbplayer/android/ui/AppRoot.kt:84-87（同类裸 launch：AppRoot.kt:92,102,111,128；PlayerScreen.kt:226,284,298；LibraryScreen.kt:108,151；SettingsScreen.kt:79,89,103,109,113）— rememberCoroutineScope 里的 launch 没有异常保护：真实 PlaybackController.playQueue（解析音频流/网络失败）或 Repository 抛异常时，未捕获异常会沿 Recomposer 的 Job 冒泡并让应用崩溃；当前 DemoGraph 不抛错掩盖了该路径 — 建议统一封装 launchSafe { runCatching { … }.onFailure { 显示错误 } }，或在各 repository/controller 内部保证不抛。`

`[P1] app/src/main/java/com/bbplayer/android/BbPlayerApp.kt:18 — 依赖图仍固定为 DemoGraph()：真实 data/playback/lyrics 模块完全未接线，搜索永远返回 4 首 demo 曲、播放是 1s ticker 假进度、歌词是硬编码 demo；真实 B 站音频与 QQ/网易云歌词链路在 App 中不会被调用（AC B/C/D 在真机上全部无法成立）— 集成阻塞（已知待接入，但验收前必须替换为真实 AppContainer）— 建议 BbPlayerApp 切换到真实 graph（data 的 BiliMusicRepository/Json*Repository/DataStorePreferences + playback.PlaybackControllerImpl + lyrics.QqNeteaseLyricsRepository），并让 DemoGraph 只用于预览。`

`[P1] app/src/main/java/com/bbplayer/android/ui/screens/library/LibraryScreen.kt:123,138；ui/screens/home/HomeScreen.kt:131,149；ui/screens/playlist/PlaylistDetailScreen.kt:84 — 用 tracks.indexOf(track) 反查“播哪一首”：历史/收藏/歌单列表一旦含重复 Track（历史按播放事件追加、同一首歌必然重复；歌单当前不允许重复但数据来源不受 UI 控制），indexOf 使用 data class 相等性只会命中最前面一条，导致点第 3 行却从第 1 行开始播放 / 点击后播错歌 — 建议 itemsIndexed 直接把 index 传给 onPlayTrack，或用 key→index 映射，不用 indexOf 反查。`

## P2

`[P2] app/src/main/java/com/bbplayer/android/ui/components/SeekLine.kt:92-99 — 拖拽 thumb 的偏移把像素当 dp 用：.padding(start = (size.width * fraction).dp)，size.width 是 px，密度 3x 时偏移放大 3 倍、fraction 大时 thumb 直接跑出进度条（进度条拖动视觉错位）— 建议 with(LocalDensity.current) { px.toDp() } 或改用 Layout/offset 的 IntOffset(px, 0)。`

`[P2] app/src/main/java/com/bbplayer/android/ui/theme/AlbumWash.kt:40-58 — 封面取色在主线程外 execute() 阻塞下载，并 BitmapFactory.decodeByteArray 全尺寸解码（无 InSampleSize、无超时；Coil 已缓存同一张图却再下载一次）；LaunchedEffect 取消不会中断阻塞的 execute，快速切歌时旧下载仍在跑，大封面解码有内存尖峰/掉帧风险 — 建议复用 Coil/OkHttp 缓存与 UA 拦截器、加 readTimeout、按目标尺寸采样解码，或直接用 Palette 的异步 API。`

`[P2] app/src/main/java/com/bbplayer/android/ui/screens/player/PlayerScreen.kt:129-131 — effectiveLyrics 在每次重组时 copy 出新 Lyrics（播放进度每个 tick 都触发重组），LyricsPanel 每次拿到新对象，整列歌词与 animateFloat/ColorAsState 全部跟着重组；同时 LyricsPanel.kt:82 的 items 未加 key，换曲时按 index 复用旧 item 状态 — 播放中每秒多次无谓分配与重组，低端机可能掉帧 — 建议 remember(lyrics, prefsOffset, localOffset) 派生 effectiveLyrics，items 增加 key = line.startMs。`

---

## 附注（不占 P 条，供验收/集成参考）

- `ui/AppRoot.kt:145`：底部 dock 的「队列」按钮实际 navigate(Player)，没有打开队列面板（PlayerScreen 内的队列入口才是有效的）；属功能入口错误，非崩溃。
- `ui/AppRoot.kt:74-78`：NavHost 统一 statusBarsPadding 后，PlayerScreen.kt:94,162 又各自 statusBarsPadding，播放页状态栏内边距会叠加；属视觉问题。
- `ui/screens/profile/ProfileScreen.kt:136-145`：热力图实际渲染 91 格（13 列），文案写「最近 12 周」；属视觉/文案。
- `ui/screens/settings/SettingsScreen.kt:109-110` 与 `PlayerScreen.kt:207,214`：歌词偏移步长 ±100ms，SPEC D4 写 ±0.5s，口径需产品确认。
- 未发现：LazyColumn 其他重复 key、Dialog/BottomSheet 未释放、协程作用域泄漏（rememberCoroutineScope 均随 composition 释放）、collectAsStateWithLifecycle initialValue 误用。
