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
