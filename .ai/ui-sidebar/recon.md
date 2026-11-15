# Recon — 主导航迁移到侧边栏 + 底栏改音乐播放控件

日期: 2026-11-15 · 执行者: Lead（本会话）· 任务 slug: `ui-sidebar`
分支: `feat/ui-sidebar-playerbar` · worktree: `/root/bbplayer/.agents/worktrees/feat/ui-sidebar-playerbar`

## 0. 并行会话约束（本次特有的隔离要求）

同一工作目录 `/root/bbplayer` 有**另一个会话**在做 v5 登录体系（SPEC-v5，L3，凭据红线）。
实测 17:48 时该会话的未提交写范围：

- 修改: `BbPlayerApp.kt`、`RealAppGraph.kt`、`LibraryScreen.kt`、`ProfileScreen.kt`、`data/BiliApi.kt`
- 新增: `ui/screens/login/`、`ui/screens/library/{CollectionDetailScreen,FavoriteFolderScreen,PagedTracks}.kt`、
  `data/{BiliContentApi,BiliContentMapper,BiliCollectionRepository,BiliFavoritesRepository,BiliUploaderRepository}.kt`、
  `data/auth/`、`data/wbi/`、`core/BiliIds.kt`、`playback/PlaybackReportTracker.kt` 等
- `main` HEAD (`53de350`) **当前不可编译**：`AppGraph` 已声明 6 个 v5 仓储，`RealAppGraph` 的接线还未提交。

因此本任务：

1. **绝不**在主 checkout（`/root/bbplayer`）切分支/改文件/跑 `git add`；本分支在其 worktree 内完成。
2. 基线取**最后一个绿点** `e71c9bd`（v4 验收包，17:29），不基于红的 `main` HEAD 开发。
3. 本分支写范围刻意避开 v5 会话已动/将动的文件（见 PLAN 写范围）；`AppRoot.kt` 是唯一潜在共享文件，
   改动保持"最小、局部、加性"，且不触碰 `AppRoutes/NavHost` 的路由表区域（对方会在此加登录/收藏路由）。
4. 合并方案：`git merge main` 到我方分支（merge-base = `e71c9bd`），冲突按 PLAN §4 处理；
   合并后必须重跑全量门禁。本轮先做**冲突预演**（`git merge-tree`，不需要对方可编译）。

## 1. 仓库事实

- 原生 Android：Kotlin 2.4.21 / Compose 1.10.0 / AGP 8.13.2 / Gradle 8.13 / compileSdk 36 / minSdk 26。
- UI 库：SaltUI 2.8.6（`io.github.moriafly:salt-ui`）+ compose-material3 1.4.0（**含 `ModalNavigationDrawer`**）。
- 模块：`:app`（UI）、`:core`（契约/模型）、`:data`、`:playback`、`:lyrics`。
- 导航：navigation-compose 2.9.8，`ui/AppRoot.kt` 内 `Routes` + 单 `NavHost`；窄屏底栏 `BottomDock`，
  宽屏（≥600dp）左侧常驻 `NavRail`。
- 当前底栏 = 迷你播放条 + 4 个纯图标导航（首页/搜索/音乐库/我的）合并于一张悬浮胶囊卡。
- 当前迷你播放条 `ui/components/MiniPlayer.kt`：封面/标题/歌手/播放暂停/队列 + 顶部 2dp 进度线。
- 播放控制契约 `core/contracts/Contracts.kt:164`：`playPause/next/previous/seekTo/...` 齐全，无需改契约。
- 设置项 `MiniPlayerStyle { DOCK(底部停靠), FLOAT(悬浮胶囊) }` 已存在且默认 DOCK
  （`core/model/V2Models.kt:145`，`SettingsScreen.kt:281`）。

## 2. 验证命令（本 worktree 内执行）

- VALIDATION_FULL: `./gradlew :app:assembleDebug :app:testDebugUnitTest`（期望 exit 0）
- VALIDATION_QUICK: `./gradlew :app:compileDebugKotlin`（期望 exit 0）
- DEVICE_SMOKE: `adb install -r app/build/outputs/apk/debug/app-debug.apk` →
  `adb shell am start -n com.bbplayer.android.debug/com.bbplayer.android.MainActivity` →
  `.ai/scripts/ui.py` dump/tap/screenshot（脚本从主 checkout 的同一文件沿用，见 `.ai/scripts/`）
- 设备：`adb devices` → `5c17d7b9`（MI 6，1080×1920 @480dpi → 360×640dp 窄屏）。
  注意：**设备与另一会话共享**，截图前用 `adb shell dumpsys package com.bbplayer.android.debug | grep lastUpdateTime`
  或界面内容确认当前 APK 是本分支构建。
- CI：无。ENFORCEMENT: prompt-only（无 pre-commit/CI required check）。
- 红线：本任务**不涉及**鉴权/凭据/schema/网络/数据删除；不新增依赖；不改 `main`。

## 3. VCS

- BASE = `e71c9bd`
- DIFF_CMD = `git diff e71c9bd..HEAD`
- LOG_CMD = `git log --oneline e71c9bd..HEAD`
- CHECKPOINT = 本地 commit（一主题一 commit）
- SPEC_BASE/SPEC_PATH 见 `.ai/ui-sidebar/SPEC_BASE`（提交 SPEC 后回填）

## 4. 设计依据（Salt Player recon）

`.ai/recon/saltplayer-design.md` §5.1/§5.2、`docs/recon` 截图要点：

- 迷你播放条：胶囊圆角、封面 44–48dp 圆角方形、标题 15–16sp Medium / 副标题 12sp、右侧播放暂停 + 队列、
  **细进度线在条底部**（非顶部、无滑钮）、点击展开播放页、左右滑切歌。
- 导航：`01-library` 截图顶栏 = 汉堡 + 标题 + 搜索；汉堡打开左侧菜单抽屉（SaltUI 同一交互模型）。
- 本次用户明确要求：**导航不再与播放条同卡**，导航放左上角侧边栏，底栏只放音乐控件。

## 5. 已知风险

1. 与另一会话的 `AppRoot.kt` 未来冲突（对方要加登录/收藏/合集路由）。
   → 我方对 `AppRoot.kt` 的改动不进入 `Routes`/`NavHost` 区域；抽屉菜单走统一的"路由跳转"回调。
2. `ModalNavigationDrawer` 默认手势会拦截横向拖拽（会与横向列表/进度条冲突）。
   → `gesturesEnabled = false`，仅汉堡按钮开抽屉（记录为可再评估）。
3. 设备共享：另一会话可能同时 `adb install`/截图。→ 截图前后校验 APK 时间戳，证据注明时间与 commit。
4. 构建资源：另一会话可能同时在跑 Gradle（同机 7GB 内存）。→ 串行、失败重试并记录。
