# BBPlayer 开发计划 v0.13.0 / v0.14.0（AI 执行版）

> 基线：`main @ a4976ff`（v0.12.0 / versionCode 21）
> 执行者：AI（按本文档逐任务实现）；发布 owner 唯一（见 `CONVENTIONS.md`）
> 已确认范围：✅ 套餐 1（体验收口）+ 套餐 2（听感强化）+ 三个专项（歌单批量下载 / OEM 媒体控件 / 仅播放横屏两栏）
> ✅ 允许新增开发依赖（见 §5）；❌ 本轮不做大屏/平板适配（C 泳道整体不做）
> 目标版本：**v0.13.0**（M1：T1–T9）→ **v0.14.0**（M2：T10–T14）

---

## 1. 本轮目标（按优先级）

| 优先级 | 任务 | 用户价值 |
|---|---|---|
| P0 | T8 歌单批量下载 | 一次选中歌单 → 全部离线 |
| P0 | T10 OEM 媒体通知优化（小米/OPPO/vivo） | 通知栏/锁屏控件好看、可操作 |
| P0 | T9 仅播放时横屏 + 横屏两栏播放页 | 横屏看封面+歌词，沉浸式 |
| P1 | T1 无障碍（TalkBack/语义/状态播报） | 可用性、读屏 |
| P1 | T2 大字号（1.3x/2.0x）适配 | 可用性 |
| P1 | T3 歌词页视觉升级 | 高级感 |
| P1 | T11 音质热切换 / T12 音频焦点 / T13 睡眠定时 UX / T14 预加载 | 听感与稳定 |
| P2 | T4 剩余空/加载/错误态、T5 主题截图回归、T6 CI lint/发布脚本、T7 PlayerScreen 拆分 | 质量与效率 |

---

## 2. 工程基线（执行前必读）

- 模块：`:app`（Compose UI/VM/DI）、`:core`（模型+契约）、`:data`（B 站接口/缓存/存储）、`:playback`（Media3/下载）、`:lyrics`（歌词解析）
- 构建/门禁命令（**必须真实跑，记录 exit code**）：
  - QUICK：`./gradlew --no-daemon :app:compileDebugKotlin`
  - AFFECTED：`./gradlew --no-daemon :app:testDebugUnitTest`
  - FULL：`./gradlew --no-daemon :data:testDebugUnitTest :playback:testDebugUnitTest :lyrics:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`
  - Release 预检：`./gradlew --no-daemon :app:assembleRelease`（R8/minify）
  - 新增（T6 完成后）：`./gradlew --no-daemon :app:lintDebug` 必须 0 error
- 设备：MI 6 / LineageOS 14 / Android 14 / 1080×1920@480dpi（类原生）。**OEM 面板必须由用户在小米/OPPO/vivo 真机上验收**，本机无法复现。
- 注意：本机内存 7.7G，曾出现双构建并发 OOM；**同一时间只跑一个 Gradle 构建**，必要时 `-Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m"`。
- 现有 UI 规范：`ui/theme/Typography.kt`（BbType 7 档）、`DesignTokens.kt`（BbSpacing/BbRadius/BbIconSize）、`ThemeTokens.kt`（中性色）、`PlayerBackdrop.kt`（播放页亮度守卫）、`Depth.kt`（阴影三处）。**新代码只能消费 token，禁止新增魔法值**。

---

## 3. 里程碑与分支

| 里程碑 | 版本 | 任务 | 分支 |
|---|---|---|---|
| M1 体验收口 | v0.13.0 | T1–T9 | `feat/v0.13-experience`（worktree） |
| M2 听感强化 | v0.14.0 | T10–T14 | `feat/v0.14-player`（worktree） |

- 一主题一 commit；每任务完成跑 AFFECTED；里程碑完成跑 FULL + Release 预检 + 真机证据，再交独立审查（fresh reviewer）与验收包。
- 只有发布 owner 能把分支 fast-forward 到 `main` 并打 tag（见 CONVENTIONS）。

---

## 4. 任务详表

### T1 无障碍（TalkBack / 语义 / 状态播报）

**目标**：可点元素全部有语义，读屏可完成「播放/切歌/收藏/进度/打开队列」。
**改动点**：
- `ui/components/BottomNavBar.kt`（dock 导航：`Role.Tab`、`selected` 状态）
- `ui/components/MiniPlayer.kt`（整条 `Role.Button` + 标题「正在播放：X，歌手 Y」；封面无私语义）
- `ui/screens/player/PlayerScreen.kt`（播放/上一首/下一首/收藏/更多/进度条语义；`stateDescription` 播报播放态与进度）
- `ui/components/SeekLine.kt`（`progressBarRangeInfo`；无障碍自定义操作 `ACTION_SET_PROGRESS`/快进 10s）
- `ui/components/States.kt`（loading `LiveRegion.Polite`；空态/错误态标题与按钮角色）
- 弹层（`OptionDialog`/`AddToPlaylistDialog`/`FavoritePickerDialog`）：`Role.RadioButton`/`Checkbox` + 容器 `paneTitle`
**AC**：
- AC1.1 全工程可点 Compose 节点 `clickable` 必带 `Role` 或 `semantics`（grep 抽查 + Accessibility Scanner 无高危）
- AC1.2 TalkBack 全流程：dock → 播放 → 暂停 → 下一首 → 打开队列 → 关闭 → 进度快进/后退，均能播报正确结果
- AC1.3 进度条可用无障碍操作调整（不依赖拖拽）
- AC1.4 无「未命名按钮」播报
**验证**：真机 TalkBack 录屏 + `grep -c "semantics {"` 对比前值（当前 2 处）+ Scanner 截图。

### T2 大字号（1.3x / 2.0x）

**目标**：系统字体放大后无截断、无重叠、无不可达按钮。
**改动点**：`TrackRow`/`SettingsScreen` 行高与 maxLines；`PlayerScreen` 固定区（标题/时间/底部 5 键）；`MiniPlayer` 标题；`HomeScreen` hero 标题；`ProfileScreen` 数字。
**AC**：
- 1.3x：全页面（发现/曲库/我的/播放/设置/搜索/歌单/下载）无截断或重叠
- 2.0x：无按钮被挤出屏幕；关键文本可读；允许滚动
- 三主题 × 1.3x 截图各 8 页归档
**验证**：`adb shell settings put system font_scale 1.3/2.0` + 截图；恢复 1.0。

### T3 歌词页视觉升级（编辑感）

**目标**：当前行突出、翻译/来源/分享卡统一，深浅纯白三主题可读。
**改动点**：`ui/screens/player/LyricsPanel.kt`（当前行 22sp/Bold；邻行实色 token 而非低 alpha；翻译行 0.72×；来源 chip「词 · 来源」；滚动交错 8–12dp）；`ui/share/ShareCard.kt`（分享卡与歌词页同字阶/色 token）；`PlayerScreen` 歌词页顶部加来源/翻译开关入口。
**AC**：
- 当前行/邻行/翻译行对比度 ≥4.5:1（当前行与翻译行；邻行为刻意 3:1 聚焦，需在验收记录说明）
- 三主题截图 + 歌词跟随滚动录屏
- 分享卡与 App 内视觉一致（字阶/圆角/主题）

### T4 剩余空/加载/错误态

**目标**：所有页面首屏不再裸转圈/裸文案。
**范围**：导入页、登录页、合集/收藏夹详情、上传者页、评论页、发现页错误、曲库空态。
**AC**：列表首屏有骨架；错误态为内联卡片（不打断布局）；空态含标题+说明+主按钮；`CircularProgressIndicator` 只允许出现在按钮内。

### T5 三主题截图回归（自动化）

**目标**：浅色/深色/纯白 × 8 页截图基线，任何 UI 改动可对比。
**方案**：新增 Roborazzi（见 §5）→ `app/src/test/java/.../screenshot/ThemeScreenshotTest.kt`，通过 `@Config(qualifiers=...)` 覆盖三主题，输出到 `app/build/outputs/roborazzi/`；同时保留真机截图脚本 `tools/ui.py`。
**AC**：CI 可跑（`./gradlew :app:testDebugUnitTest` 内生成）；基线图入库 `docs/dev/screenshots/`（仅基线，体积压缩）；改动后 diff 人工确认。

### T6 CI lint + 发布脚本 + app 测试扩容

**目标**：门禁自动化、发布可重复。
**改动点**：
- `.github/workflows/android.yml` 增加 `:app:lintDebug`（fail on error）与（可选）ktlint
- 新增 `tools/release.sh`：参数 `X.Y.Z` → 校验 clean → 改 `versionCode/versionName` → 更新 README 版本段 → `assembleRelease` → commit `chore(release): vX.Y.Z` → 提示人工确认后 `git tag/push`
- app 单测 29 → ≥45：覆盖 `Format`、`ThemeTokens`、`BbMotion`、下载队列聚合、歌词滚动计算
**AC**：lint 0 error；release.sh 干跑（--dry-run）输出正确；app tests ≥45 全绿。

### T7 PlayerScreen 拆分（第一步，无行为变化）

**目标**：1374 行拆分，降低后续 T3/T9/T13 改动风险。
**拆分**：`ui/screens/player/TransportControls.kt`（传输行+底部功能行）、`PlayerDialogs.kt`（5 个弹层）、`PlayerTopBar.kt`；`PlayerScreen.kt` 只保留状态/背景/Pager 编排。
**AC**：行为/截图与拆分前一致（三主题对比）；单测仍绿；无 API 变化。

### T8【专项】歌单批量下载

**目标**：在歌单详情页一键把歌单内全部歌曲加入下载队列；可查看进度、失败重试、已下载自动跳过。
**改动点**：
- `ui/screens/playlist/PlaylistDetailScreen.kt`：顶部 action「下载全部」（已有歌单 `Playlist` 与 tracks）
- `ui/screens/library/LibraryScreen.kt`：歌单行 More 菜单新增「下载全部」
- `core`/`AppGraph`：新增 `DownloadRepository.enqueueAll(tracks): Flow<BulkDownloadState>`（默认实现：串行 `enqueue` + 统计），新契约需在 `V2Contracts.kt` 增补并在 `BiliDownloadManager` 实现
- `ui/screens/downloads/DownloadsScreen.kt`：批量任务进度条（完成/失败/跳过数）
- 设置：可选「仅 Wi-Fi 下载」（新增 `PreferencesRepository.wifiOnlyDownload`，默认开；`BiliDownloadManager` 在非 Wi-Fi 时排队等待）
**AC**：
- AC8.1 30 首歌单点「下载全部」：进入队列，按 `downloadParallel` 并发，全部完成；重复点击幂等
- AC8.2 已下载曲目跳过；失败曲目可单条重试；关闭 App 重启后任务续跑（沿用 `DownloadIndex`）
- AC8.3 通知/下载页能看到批量进度；无 Wi-Fi 时按设置暂停并提示
- AC8.4 不改变单曲下载现有行为与数据结构（向后兼容）
**风险**：批量接口风控（沿用现有 350ms/1200ms 限速）；后台限制（前台服务已有）。**回滚**：移除入口 + 保留单曲下载即可，不影响已下载数据。

### T9【专项】仅播放时横屏 + 横屏两栏播放页

**目标**：只有全屏播放页允许横屏；其他页面始终竖屏。横屏播放页参考网易云：左封面右歌词，全屏沉浸。
**实现**：
- `MainActivity` 默认 `requestedOrientation = SCREEN_ORIENTATION_PORTRAIT`；`PlayerScreen` 进入时设置 `SCREEN_ORIENTATION_FULL_SENSOR`（允许旋转），退出恢复 `PORTRAIT`（`DisposableEffect` + 保存原值）
- `AndroidManifest.xml` 已 `configChanges="orientation|screenSize..."`，无需改；确保 Activity 不重建丢状态
- 横屏布局（`PlayerScreen`）：`BoxWithConstraints`；宽>高时：左 42% 封面（复用 `CoverPage` 尺寸逻辑）+ 右 58% `LyricsPanel`（当前行居中）；传输/进度固定在左栏底部；隐藏 Pager 圆点；评论改为「更多」弹层入口
- 全屏沉浸：横屏播放页 `WindowInsetsControllerCompat` 隐藏状态栏（保留手势条），退出恢复；可选「保持屏幕常亮」沿用
**AC**：
- AC9.1 仅播放页可横屏；返回/切到其他页立即回竖屏且无闪白
- AC9.2 横屏播放页左封面右歌词、歌词跟随当前行，控制可用，无溢出
- AC9.3 旋转 3 次状态不丢（播放位置/歌词滚动/收藏态）
- AC9.4 录屏证据（竖→横→竖）

### T10【专项】OEM 媒体通知优化（小米/OPPO/vivo）

**问题**：AOSP 正常；MIUI/HyperOS、ColorOS、OriginOS 使用自有媒体面板（见 `.ai`→现 `docs/reference/evidence/miui-media-controls-2026-11-22.jpg`）：自带模糊封面背景、系统排版、仅 3 个操作、忽略自定义 `RemoteViews`/compact actions；当前在小米上表现为：封面缩略图小、标题截断、副标题显示 UP 主而非歌词、面板底色由封面模糊决定（暗封面会显「脏」）。
**实现要点**：
- `MediaSession`（`PlaybackService`）补齐：`setActive(true)`、完整 `PlaybackState.actions`（PLAY_PAUSE/SKIP_NEXT/PREV/SEEK_TO/SET_RATE/STOP）、`MediaMetadata`（title=短标题、artist=UP 主、albumTitle=BBPlayer、artworkData 512²+artworkUri、`setIsBrowsable(false)`、`setIsPlayable(true)`）、`setSessionActivity`（点卡片回 App 播放页）、`setQueue`
- `LyricNotificationProvider`（`getNotificationContentText`）仅对 AOSP 生效；OEM 面板读 `MediaMetadata.artist/album`，因此提供设置「通知栏副标题：UP 主 / 当前歌词」，默认「UP 主」（OEM 兼容），歌词模式在 AOSP 与部分 ROM 生效
- 提供「通知封面增强」：把封面图做轻度提亮/对比度处理（512×512，避免暗封面导致面板底色脏），仅在通知使用（不改变 App 内封面）
- 通知渠道 `IMPORTANCE_LOW` + `CATEGORY_TRANSPORT` + `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`；确认小图标为纯白 silhouette
- 不用 `setCustomContentView`、不用非标准 actions、不依赖系统强调色
**AC**：
- AC10.1 AOSP（MI6 Lineage）通知全功能且歌词行正常
- AC10.2 用户真机（MIUI/HyperOS、ColorOS、OriginOS 各 ≥1 台）截图：封面清晰、标题/歌手不截断到不可读、三键可用、点卡片进播放页
- AC10.3 面板底色不再「脏」（封面增强后）；如用户仍不满意，提供「纯色/极简封面」作为通知封面选项
- AC10.4 不引入第三方媒体库；不劣化 AOSP
**风险**：无 OEM 设备时只能按官方媒体规范做 best-effort；需用户协助真机验证并回传截图。

### T11 音质热切换（不重启播放）

**目标**：设置/播放页切音质后，无缝重启当前曲目到新流（保留位置、播放态），失败回退旧流并提示。
**改动点**：`PlaybackControllerImpl`（`setQuality` 路径重建 DASH/流）；错误回退；`SettingsScreen` 音质切换即时生效提示。
**AC**：切音质后 ≤1s 恢复播放且进度连续；无损需登录；失败回退并 Toast；三首不同格式曲目验证。

### T12 音频焦点 / 耳机 / 来电

**目标**：来电、其他 App 播音、耳机拔出行为符合系统预期。
**改动点**：Media3 `AudioFocus` 已有默认；补 `setHandleAudioBecomingNoisy(true)`（拔出暂停）、来电时暂停不自动恢复、`setAudioAttributes(usage=MEDIA, contentType=MUSIC)`；通知/服务字节。
**AC**：来电暂停、结束后不自动播；拔耳机暂停；其他 App 播报时 duck/暂停符合预期（真机验证清单）。

### T13 睡眠定时 UX

**目标**：倒计时可见、到点淡出暂停（而非硬停）、可取消/延长。
**改动点**：`TransportControls` 定时入口显示剩余分钟；`PlaybackControllerImpl` 定时到点用 3s 音量淡出再暂停；通知/播放页显示剩余时间。
**AC**：设置 1 分钟验证淡出；取消/延长生效；回前台状态同步。

### T14 预加载 / 缓存可视化

**目标**：下一首提前解析/缓冲；缓存占用可见可清。
**改动点**：`PlaybackControllerImpl` 监听队列变化预取下一首 URL（复用 `MusicRepository.getAudioStream` + 缓存）；`SettingsScreen`「缓存」显示音频缓存大小（区别于接口缓存）；`BiliDownloadManager` 已完成下载占用统计。
**AC**：切下一首首响 <300ms（缓存命中）；缓存统计准确到 MB；清缓存不影响已下载。

---

## 5. 新增依赖（已获用户批准，需在 `gradle/libs.versions.toml` 固定版本）

| 依赖 | 用途 | 备注 |
|---|---|---|
| `androidx.compose.ui:ui-test-junit4` | Compose UI 语义/交互测试 | 版本对齐 compose 1.10.0 |
| `androidx.test:core` / `androidx.test.ext:junit` | 测试运行器 | 最新稳定 |
| `org.robolectric:robolectric` | JVM 上跑 Compose UI 测试 | 无需设备 |
| `io.github.takahirom.roborazzi:roborazzi` + compose 支持 | 三主题截图回归（T5） | 插件 + test 依赖 |
| `org.jlleitschuh.gradle.ktlint`（或 detekt 二选一） | 静态检查（T6） | 先 AGP lint，再加 ktlint |
| `androidx.profileinstaller:profileinstaller` | baseline profile（可选，T6 之后） | 需配 macrobenchmark，单独评估 |

约束：不引入统计/崩溃 SDK、不引第二套图标库、不引音频引擎；每个新依赖需在 PR/验收包说明 license 与 R8 影响。

---

## 6. 协作与发布约定

见 [CONVENTIONS.md](CONVENTIONS.md)（分支命名、commit 格式、SPEC/PLAN/REVIEW/ACCEPTANCE、单一发布 owner、tag/Release 流程、设备证据、文档保留规则）。

---

## 7. 门禁与验收（每个里程碑）

1. FULL gate EXIT=0（§2 命令）+ `assembleRelease` 成功
2. `lintDebug` 0 error（T6 起）
3. 真机证据：三主题截图矩阵、TalkBack 录屏、横屏录屏、批量下载 E2E、OEM 面板截图（用户提供）
4. 独立审查（fresh reviewer）P0/P1 清零
5. 验收包（模板见 `docs/dev/templates/ACCEPTANCE.md`）+ 用户勾选

---

## 8. 风险与回滚

| 风险 | 缓解 |
|---|---|
| 横屏切换 Activity 重建丢状态 | `configChanges` 已含 orientation；用 `DisposableEffect` 精确恢复；录屏回归 |
| OEM 面板差异无法本机复现 | 按官方媒体规范做；提供兼容开关与用户真机清单；不承诺 100% |
| 批量下载触发 B 站风控 | 沿用现有限速/退避；单批 ≤50；失败可续 |
| 音频链路改动导致断播 | T11/T12 走独立分支 + 真机矩阵；保留旧路径开关 |
| 新依赖体积/构建时间 | 仅 test 依赖不影响 APK；profileinstaller 单独评估 |

---

## 9. 清理记录（本轮已完成，详见 `docs/dev/CLEANUP.md`）

- 已合并/无用分支本地与远端删除；worktree 移除
- 历史 `.ai/**` 临时文档删除，保留内容迁至 `docs/reference/`（设计/上游 recon）与 `docs/dev/`（规范/模板）
- 发布历史汇总至 `docs/RELEASES.md`；tag 全部保留
- 产品截图保留 `docs/screenshots/`（README 使用）

## 10. 附录：关键文件索引

| 任务 | 文件 |
|---|---|
| T1/T2 | `ui/components/*`、`ui/screens/player/PlayerScreen.kt`、`SettingsScreen.kt` |
| T3 | `ui/screens/player/LyricsPanel.kt`、`ui/share/ShareCard.kt` |
| T4 | `ui/components/States.kt`、`CoverImage.kt`、各 `screens/*` |
| T5/T6 | `.github/workflows/android.yml`、`tools/release.sh`、`app/src/test/**` |
| T7 | `ui/screens/player/`（拆分目标目录） |
| T8 | `playback/.../download/BiliDownloadManager.kt`、`core/.../V2Contracts.kt`、`PlaylistDetailScreen.kt`、`DownloadsScreen.kt` |
| T9 | `MainActivity.kt`、`AndroidManifest.xml`、`ui/AppRoot.kt`、`PlayerScreen.kt` |
| T10 | `playback/PlaybackService.kt`、`playback/LyricNotificationProvider.kt`、`PlaybackControllerImpl.kt` |
| T11–T14 | `playback/PlaybackControllerImpl.kt`、`SettingsScreen.kt`、`DownloadsScreen.kt` |
