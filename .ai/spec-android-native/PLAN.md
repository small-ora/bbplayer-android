# PLAN v1 — BBPlayer 安卓原生版

## 写范围与并行分工（文件级，互不重叠）
| 角色 | 写范围 |
|---|---|
| Lead | 根构建文件、`app/src/main/java/com/bbplayer/android/{BbPlayerApp,MainActivity}.kt`、`core/model/**`、`core/contracts/**`、`di/**`、`ui/theme/**`、`ui/components/**`、`ui/navigation/**`、`ui/screens/player/**`、`ui/screens/lyrics/**`、`app/src/main/res/**`、集成修复 |
| T-data | `data/api/**`、`data/repo/**`、`data/store/**`、`data/lyrics/**`(provider/cache)、`app/src/test/**/data/**` |
| T-playback | `playback/**`（service/controller/queue/timer/notification/quality/stream resolver）、`app/src/test/**/playback/**` |
| T-screens | `ui/screens/home/**`、`ui/screens/search/**`、`ui/screens/library/**`、`ui/screens/profile/**`、`ui/screens/settings/**`、`ui/viewmodel/{home,search,library,profile,settings}/**` |
| T-lyrics | `lyrics/**`（parser/matcher/offset）、`app/src/test/**/lyrics/**` |

## 步骤
0. 工程骨架（Lead）: Gradle/AGP/Kotlin/SaltUI 依赖打通；最小 Compose 页面；构建+安装+截图绿。→ CHECKPOINT
1. 契约层（Lead）: core/model 数据模型、core/contracts 接口（MusicRepositoryLib/LibraryRepository/HistoryRepository/PreferencesRepository/PlaybackController/ LyricsEngine）、DI 容器、Nav 骨架、设计 token 封装。→ CHECKPOINT
2. 并行实现（T-data / T-playback / T-lyrics / T-screens）: 各包内实现 + 单测；每步 `./gradlew :app:compileDebugKotlin`。→ CHECKPOINT/人
3. 集成（Lead）: 首页/播放页/歌词页/迷你条/底部导航接真实数据；真机走查。→ CHECKPOINT
4. 自检（Lead+全员）: `assembleDebug` + `testDebugUnitTest` + 真机 smoke（安装/启动/搜索/播放/歌词/主题）+ 截图证据。→ evidence/
5. 独立审查（fresh reviewer）: 按 SPEC_BASE..HEAD 审 diff。→ REVIEW.md
6. 验收包: ACCEPTANCE.md + 截图 + APK 路径。→ 人

## 每步验证命令
- QUICK: `./gradlew :app:compileDebugKotlin`
- FULL: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- DEVICE: `adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.bbplayer.android.debug/com.bbplayer.android.MainActivity && adb exec-out screencap -p > .ai/evidence/shot.png`

## 重分级触发
改动超出写范围 / 新增登录或写操作类 API / 新增依赖 / 需要改 SPEC 的 AC → 停下来问用户。
