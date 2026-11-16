# PLAN — 底部导航 + 悬浮迷你播放器重构

对应 SPEC: `.ai/ui-bottomnav/SPEC.md`（冻结于 SPEC_BASE）。基线 `c415b02`。

## 写范围与并行划分（一写者一文件，互不重叠）

| 步骤 | 主题 | 写范围（文件） | 负责 | 验证 |
|---|---|---|---|---|
| P0 | SPEC 冻结 + 设计 token/常量 | `.ai/ui-bottomnav/*`、`ui/theme/DesignTokens.kt`、`ui/theme/BbTheme.kt` | Lead | compile |
| P1 | 全局骨架：去抽屉、底部导航、迷你播放器、路由联动、播放页转场 | `ui/AppRoot.kt`、`ui/components/BottomNavBar.kt`(新)、`ui/components/MiniPlayer.kt`(新)、`ui/components/BottomPlayerBar.kt`(删)、`ui/components/NavDrawer.kt`(删)、`ui/components/Headers.kt` | Lead | compile + 真机 |
| P2 | 发现页 | `ui/screens/home/HomeScreen.kt` | 队友 A | compile + 截图 |
| P3 | 曲库页 | `ui/screens/library/LibraryScreen.kt` | 队友 B | compile + 截图 |
| P4 | 我的页 | `ui/screens/profile/ProfileScreen.kt` | 队友 C | compile + 截图 |
| P5 | 播放页 | `ui/screens/player/PlayerScreen.kt` | 队友 D | compile + 截图 |
| P6 | 二级页防遮挡 padding | Settings/PlaylistDetail/Downloads/History/Leaderboard/Import/WatchLater/FavoriteFolder/CollectionDetail/Uploader/Comments 10 文件 | 队友 E | compile |
| P7 | 集成修复 + 门禁 + 真机冒烟 + 截图 | 冲突文件以 Lead 为准 | Lead | FULL |
| P8 | 独立审查 | 只读 | fresh reviewer | REVIEW.md |
| P9 | 验收包 | `.ai/ui-bottomnav/ACCEPTANCE.md` | Lead | — |

## 每步验证与回滚

- 每步：`flock /tmp/bbplayer-gradle.lock ./gradlew :app:compileDebugKotlin`（并行时串行，exit 0 即绿）。
- CHECKPOINT：每步一个本地 commit（Lead 在集成后按主题补提交；队友不 commit，避免并发 git index 竞争）。
- 回滚点：任意步骤 `git reset --hard <上一步 commit>`；main 未改动。

## 依赖

- P1 依赖 P0（token/常量）。
- P2–P6 只依赖 P0；与 P1 并行。P2–P6 之间文件不重叠。
- P7 依赖 P0–P6 全绿。
- P8 依赖 P7。

## 人工门禁 / 重分级触发

- 视觉判断（胶囊高度、热力图密度、播放器层级）以真机截图交人验收。
- 出现以下情况停止并问人：需要新增依赖 / 需要改 `core` 契约 / 需要动 schema 或鉴权 / 需求 AC 冲突。

## 执行记录

- （实现中追加）

## 执行记录（Lead）

- P0–P6 全部完成。并行 5 队友（home/library/profile/player/padding），写范围互不重叠；
  共享骨架（AppRoot/Headers/theme/MiniPlayer/BottomNavBar）由 Lead 独占。
- 环境事实：会话沙箱 `workspace-write` 禁止写 `/root/.gradle` 与 `/root/.android`；
  统一改用 `GRADLE_USER_HOME=/root/bbplayer/.gradle/user-home`
  与 `ANDROID_USER_HOME=/root/bbplayer/.gradle/android-user-home`（gitignored）。
- 集成后真机（MI 6，360×640dp，gesture nav）冒烟发现并修复 3 处：
  1) 我的页卡片内边距：SaltUI `RoundedColumn(paddingValues=…)` 是**卡片外**留白，
     导致热力图/文字贴边 → 改为卡片内 `Column(Modifier.padding(16.dp))`。
  2) 曲库页标题「音乐库」与 Tab「曲库」不一致 → 统一「曲库」。
  3) 播放器封面被底部固定区挤压（264dp/73%）→ 标题+副标题合并、压缩间距，
     播放态 306dp = 屏宽 85%，暂停态 297dp = 82.4%，均落在 AC10 区间。
- 全量门禁 `:app:assembleDebug + app/data/playback/lyrics 单测 --rerun-tasks`：exit 0（见 evidence/final-gate.log）。
- 真机冒烟截图：`.ai/ui-bottomnav/evidence/*.png`（发现/曲库/我的/播放/二级页/深色/滚到底/批量管理）。
- 未能真机覆盖：宽屏 ≥600dp NavRail 分支（无平板设备）；歌词/评论页未重排（非目标）。
