# Recon — 底部导航 + 悬浮迷你播放器 UI/UX 重构

日期: 2026-11-16 · 执行者: Lead · slug: `ui-bottomnav`
分支: `feat/ui-bottomnav-refresh` · BASE = `c415b02`（v0.7.4 发布回填，main 干净）

## 1. 仓库事实（只读勘察）

- 原生 Android：Kotlin 2.4.21 / Compose 1.10.0 / AGP 8.13.2 / Gradle 8.13 / compileSdk 36 / minSdk 26。
- 模块：`:app`（全部 UI）、`:core`、`:data`、`:playback`、`:lyrics`；UI 库 = SaltUI 2.8.6（LGPL-2.1）+ material3 1.4.0 + navigation-compose 2.9.8。
- 当前导航（v6 起）：`AppRoot.kt` 用 `ModalNavigationDrawer` + 左上汉堡（`LocalOpenMenu`），
  底栏只有 `BottomPlayerBar`（音乐控件）；宽屏 ≥600dp 左侧常驻 rail。无 Bottom Navigation。
- 当前页面：Home（大标题「发现音乐」+ 搜索 pill + 最近播放/歌单/收藏/统计/热门）、Library（歌单行右侧
  [播放][重命名][删除] 胶囊）、Profile（统计卡 + 12 周热力图 + 功能列表）、Player（三页横滑）、
  二级页若干（Settings/PlaylistDetail/Downloads/History/Leaderboard/Import/WatchLater/FavoriteFolder/
  CollectionDetail/Uploader/Search/Comments）。
- 无「识别」FAB：全仓库无 `FloatingActionButton`、无「识别」入口（需求文档描述的是上游/旧版控件）。
- 底部 contentPadding 现状：根页 140dp；二级页 40/120dp，小于迷你播放器高度，会遮挡最后一项。
- `MiniPlayerStyle {DOCK, FLOAT}` 偏好存在于设置页（默认 DOCK），与新规范「固定悬浮胶囊」冲突。
- 主题：`BbTheme.kt` 把 M3 动态色映射到 SaltColors；浅色背景用的是 dynamic surface（非 #F8F9FA），
  Salt 卡片圆角 `shapes.medium` = 12dp（需求要求 16dp），卡片色 = `subBackground`。

## 2. 验证命令

- VALIDATION_FULL: `./gradlew :app:assembleDebug :app:testDebugUnitTest`（期望 exit 0；另跑
  `:data:testDebugUnitTest :playback:testDebugUnitTest :lyrics:testDebugUnitTest` 与 CI 对齐）
- VALIDATION_QUICK: `./gradlew :app:compileDebugKotlin`（期望 exit 0；并行写者用
  `flock /tmp/bbplayer-gradle.lock ./gradlew ...` 串行）
- DEVICE_SMOKE: `adb install -r app/build/outputs/apk/debug/app-debug.apk` →
  `adb shell am start -n com.bbplayer.android.debug/com.bbplayer.android.MainActivity` →
  `.ai/scripts/ui.py screenshot/tap/dump`。设备：MI 6 `5c17d7b9`，1080×1920 @480dpi = 360×640dp，
  Android 14（sdk 34），gesture navigation（navigationBars inset ≈ 24dp），网络可达 B 站。
- CI: `.github/workflows/android.yml` 存在但本任务不 push，不触发。
- ENFORCEMENT: prompt-only（无 pre-commit / 无 required check）。

## 3. VCS / 回滚

- DIFF_CMD = `git diff c415b02..HEAD`；LOG_CMD = `git log --oneline c415b02..HEAD`；CHECKPOINT = 本地 commit。
- SPEC_BASE / SPEC_PATH 见 `.ai/ui-bottomnav/SPEC_BASE`。
- 回滚：`git checkout main`（本任务全部在分支上，main 未动）。

## 4. 红线与约束

- 不改 `:data` / `:playback` / `:lyrics` / `:core`，不改鉴权、网络、缓存、scheme、依赖版本。
- 不新增第三方依赖；不 push、不开 PR、不合入。
- `:app` 内多写者按文件划分写范围（见共享任务板），共享文件 `AppRoot.kt`/`Headers.kt`/theme 只由 Lead 写。
- 生成物（APK、截图）不入库；`evidence/` 已被 gitignore。

## 5. 已知风险

1. SaltUI 动态色的 neutral 需要覆盖成 #F8F9FA/#EDF2F7，同时保留 dynamic primary —— 若覆盖过宽可能影响
   深色/纯白模式，需真机逐一截图回归 4 个主题组合。
2. `NavigationBar` 自带 windowInsets；与迷你播放器叠加时容易 double-inset，必须统一由外层
   `navigationBarsPadding()` 管理。
3. `SearchBar` 在 material3 1.4.0 有两种 API（新 `SearchBarState` 版与旧 `inputField` 版）；旧版已
   deprecated 但可用，作为「点击跳搜索页」的轻量 header 风险最低。
4. 设备与另一会话不共享（本机只有本会话在用 adb），但 APK 覆盖会改变已安装版本 → 冒烟前记录 commit。
