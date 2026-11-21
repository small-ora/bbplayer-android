# 验收包 — BBPlayer 移动端 UI/UX 重构（底部导航 + 悬浮迷你播放器）

**级别**：L2（跨 31 文件、信息架构变更、含产品取舍；见 SPEC 分级说明）
**已执行阶段**：0 勘察 → 1 隔离分支 → 2 SPEC 冻结 → 3 PLAN → 4 实现（并行 5 写者）→ 5 自检（全量门禁 + 真机冒烟）→ 6 独立审查（2 位 fresh 审查者）→ 7 验收包
**跳过阶段及理由**：8 合入（按工作流默认不 push / 不合入，等人工授权）；CI 未触发（未 push）；无 VCS 例外（git 仓库，localhost 分支）
**状态**：**人工验收已通过（2026-11-16）→ 已提交 main 并发布 v0.8.0**（release/CI 结果见 §11 回填）

## 1. 一句话结果

侧边抽屉导航已废弃，改为底部 3 Tab（发现/曲库/我的）+ 悬浮胶囊迷你播放器；发现页改 M3 SearchBar
Header 与 16:9 视频卡；曲库页三胶囊改为 MoreVert + M3 ModalBottomSheet（含批量管理/二次确认）；
我的页合并统计+12 周热力图仪表板并统一入口；播放器封面 82–85% 屏宽、标题跑马灯+常驻红心、
底部手势安全区与主播放键层级按规范重排；视觉 token 统一（浅色 #F8F9FA / 卡片 #EDF2F7 / 16dp 圆角 /
20sp Bold）。全量门禁 exit 0，真机（MI 6，360×640dp，Android 14）逐页截图验证。

## 2. 交付物与复现

| 项 | 值 |
|---|---|
| 分支 | `feat/ui-bottomnav-refresh`（main 未动） |
| BASE | `c415b02` |
| SPEC_BASE / SPEC_PATH | `7d5f18d` / `.ai/ui-bottomnav/SPEC.md`（实现期间 SPEC 未改动：`git diff 7d5f18d..HEAD -- .ai/ui-bottomnav/SPEC.md` 为空） |
| 最终 HEAD | 见 `git log --oneline v0.7.4..v0.8.0`（发布 commit 含 versionCode 13 / versionName 0.8.0） |
| DIFF_CMD | `git diff c415b02..HEAD`（31 files，+1492 / −904） |
| LOG_CMD | `git log --oneline c415b02..HEAD` |
| ENFORCEMENT | `prompt-only`（无 pre-commit / 无 required check；本流程靠自觉 + 证据） |
| CI | push main / tag v0.8.0 触发 `.github/workflows/android.yml`；结果回填见 §11 |
| 全量门禁 | `cd /root/bbplayer && ANDROID_USER_HOME=.gradle/android-user-home GRADLE_USER_HOME=.gradle/user-home ./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :data:testDebugUnitTest :playback:testDebugUnitTest :lyrics:testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL / 138 tasks executed / exit 0`（`evidence/final-gate.log`） |
| 真机冒烟 | MI 6 `5c17d7b9`，安装最终 debug APK 启动无崩溃；证据 `evidence/01–12*.png` |

## 3. 改动清单（按主题；完整 diff 见 DIFF_CMD）

| 主题 | 文件 | 为什么 |
|---|---|---|
| 主题 token | `ui/theme/BbTheme.kt`、`ui/theme/DesignTokens.kt` | 浅色 #F8F9FA/#EDF2F7、卡片 16dp、20sp Bold、动态主色保留；尺寸/内容安全区单一事实源 |
| 导航骨架 | `ui/AppRoot.kt`、新增 `components/BottomNavBar.kt`、新增 `components/MiniPlayer.kt`、删除 `components/NavDrawer.kt`、删除 `components/BottomPlayerBar.kt` | 去抽屉/汉堡，底部 3 Tab；迷你播放器悬浮胶囊 + 二级页底栏下滑隐藏/播放器下沉；播放页滑入转场 |
| 页面标题 | `ui/components/Headers.kt` | 去汉堡入口，标题 20sp Bold |
| 发现页 | `screens/home/HomeScreen.kt` | M3 SearchBar Header、删大标题/统计卡、视频卡 16:9 |
| 曲库页 | `screens/library/LibraryScreen.kt` | MoreVert + ModalBottomSheet、批量管理/确认、无 FAB、标题统一「曲库」 |
| 我的页 | `screens/profile/ProfileScreen.kt` | 仪表板卡（左指标右 12×7 热力图）、统一 ripple 入口、右上设置齿轮 |
| 播放页 | `screens/player/PlayerScreen.kt`、`components/SeekLine.kt` | 封面 85%/暂停 82.4%、16dp 无描边、标题跑马灯+红心、UP主+音质、72dp 主键、底部手势安全区；SeekLine 支持紧凑热区 |
| 设置 | `screens/settings/SettingsScreen.kt` | 删除已废弃「迷你播放器样式」项；二级页 padding |
| 二级页防遮挡 | Login/Search/PlaylistDetail/Downloads/History/Leaderboard/Import/WatchLater/FavoriteFolder/CollectionDetail/Uploader/Comments | 底部 contentPadding 140dp，避免迷你播放器遮挡最后一项 |
| 工程 | `.gitignore`、`.ai/ui-bottomnav/*` | 忽略 `.kotlin/`；recon/SPEC/PLAN/REVIEW/证据（`.ai/evidence` 系列默认不入库） |

## 4. AC 对照与证据

12 条 AC 逐条对照见 `evidence/AC-evidence.md`（命令 + 截图 + 源码位置）。摘要：

- AC1–AC5（导航/迷你播放器/路由）✅ 截图 01–07、09–12
- AC6（防遮挡）✅ 根页动态 padding（chrome + 系统 inset）、二级页 140dp、列表裁剪到状态栏下；截图 09/11/12
- AC7（发现页）✅ 截图 01
- AC8（曲库）✅ 截图 02/03/10/10b（批量管理 + 二次确认）
- AC9（我的）✅ 截图 04
- AC10（播放器）✅ 截图 05-player-paused/playing；像素测量 306dp=85.0%、296.7dp=82.4%
- AC11（视觉 token）✅ 浅色 01/02/04、深色 08-dark-*、纯白 08-purewhite-*
- AC12（门禁）✅ `final-gate.log` exit 0

## 5. 独立审查结论

- 审查者：2 位 fresh（1 个 spawn subagent、1 个独立 workflow agent），均未参与实现、只读。
- 依据：`git show 7d5f18d:.ai/ui-bottomnav/SPEC.md` + `git diff 7d5f18d..HEAD -- app/src/main/java`。
- 结果：无 P0；1×P1（根页底部安全区未计系统 inset）+ 5×P2（登录页缺底部 padding、发现页滚动压状态栏、
  批量删除无确认、BottomSheet 动作竞态、未用 token/双份事实源）全部修复；另补齐 AC10/AC11 截图。
- 审查者点名项：无断言弱化、无 skip/only/ignore、无测试/CI/lint 配置改动、无快照/lockfile/依赖变更、
  SPEC/规则未改、无鉴权/网络/密钥改动。
- 详情：`.ai/ui-bottomnav/REVIEW.md`。

## 6. 风险与回滚

- 回滚：`git checkout main`（本任务全部在分支上，main 未动）；或逐个 `git revert 47bbb5a..9f435bc`。
- 未在真机验证：宽屏 ≥600dp 的 NavRail 分支（无平板，仅源码审查）；3 键导航设备（inset 由系统动态计算，
  代码已加 inset；未实机回归）；纯白主题仅发现/我的页截图。
- 已知取舍：`MiniPlayerStyle` 偏好废弃（设置项已删，枚举保留兼容数据）；批量管理为最小实现（多选+批量删除+确认）；
  「识别」FAB 本就不存在，按"无纯色 FAB + 操作收敛顶栏"验收（SPEC 假设 1）。
- 环境备注：沙箱禁止写 `/root/.gradle`、`/root/.android`，门禁使用工作区内 gitignored 的
  `GRADLE_USER_HOME`/`ANDROID_USER_HOME`（不影响仓库产物）。

## 7. 我替你做的判断与假设（可推翻）

1. 需求「约 140dp」：根页取 chrome 150dp + 系统 inset（本机 156dp），二级页 140dp——为保证最后一项不被遮挡。
2. 批量管理实现为歌单多选 + 批量删除（含二次确认），未做导入/下载等其它批量场景。
3. 迷你播放器固定为悬浮胶囊（删除设置里的样式开关，枚举保留兼容）。
4. 宽屏保留左侧 NavRail（自适应），不强制底部导航。
5. 搜索页、详情页、设置页仅统一底部 padding，不重做信息架构；歌词/评论页未重排。
6. 发现页 SearchBar 在纯白主题补 0.5dp 描边（否则与白底同色不可见），正常主题视觉不变。

## 8. 需要你验收的 3 件事（≤10 分钟）

1. 真机看主流程：底部 3 Tab 切换、迷你播放器（点击/上滑展开、左右滑切歌）、播放页三页横滑与返回。
2. 打开「曲库 → 歌单 MoreVert」：确认弹层四项可用、批量管理多选+删除确认；再看「我的」仪表板排版密度。
3. 抽查截图（`evidence/01-discover.png`、`05-player-playing.png`、`04-profile.png`、`10b-library-batch-confirm.png`、
   `08-dark-discover.png`）是否符合你的视觉预期；不满意可指定页面继续调。

## 9. 明确未做

- 未 push、未开 PR、未合入、未发版（版本号仍 0.7.4，未改 versionCode）。
- 未新增依赖、未改 `:data/:playback/:lyrics/:core`、未动鉴权/网络/密钥/schema。
- 未新增自动化 Compose UI 测试（SPEC 非目标禁止新增依赖）；新增有状态逻辑由真机冒烟覆盖。
- 未做宽屏真机、3 键导航真机、纯白主题全页面回归；未录像动效。

## 10. 结论

- [x] 保留检查点（分支 `feat/ui-bottomnav-refresh`，本地 commit）
- [x] 授权 push / 合入（2026-11-16 人工验收通过）
- [ ] 修改后通过（请指出页面/细节）
- [ ] 打回
- [ ] 无法验收（请说明环境/证据缺口）

## 11. 发布记录（v0.8.0）

- 版本：`versionCode 13` / `versionName 0.8.0`（包名 `com.bbplayer.android`，固定 release 签名沿用 v0.7.1+）
- 合并方式：`git checkout main && git merge feat/ui-bottomnav-refresh` → `git push origin main`
- Release：annotated tag `v0.8.0` → CI `Publish GitHub Release` 自动附 `app-release.apk`
- 发布结果：✅ 2026-11-16
  - Release：https://github.com/small-ora/bbplayer-android/releases/tag/v0.8.0
  - 资产：`app-release.apk`（4,454,611 B，固定 release 签名；CI `Verify release signature` 通过）
  - CI（tag）：Android CI run `43832493278` success（单测 → debug APK → signed release APK → Release）
  - CI（main）：Android CI run `43832488764` success
  - 产物校验：`aapt2 dump badging` → versionCode 13 / versionName 0.8.0（debug 与 release 均一致）

## 12. v0.8.1 补丁发布记录

- 触发：人工验收反馈「系统手势条（小白条）那一块透出列表内容」（0.8.0 在 navigationBars inset 较大的手机上暴露）
- 根因：底部 chrome（导航栏 + 迷你播放器）外层只有 `navigationBarsPadding()`，inset 区域内绘制的是页面内容；页面列表底边 padding 只保证不遮挡可点内容，不负责铺底
- 修复：`AppRoot.kt` 底部 chrome 在 `navigationBarsPadding()` **之前**加 `background(SaltTheme.colors.background)`（background 为外层 modifier，覆盖含 inset 的整条区域）；宽屏迷你播放器包进带背景的 Box
- 验证：真机用 36dp 模拟 navigationBars inset（临时 padding）截图确认 strip 为纯背景无内容透出；撤销模拟后全量门禁 exit 0（debug+release+四模块单测 --rerun-tasks）
- Release：✅ 2026-11-16 — https://github.com/small-ora/bbplayer-android/releases/tag/v0.8.1
  - 资产：`app-release.apk`（4,454,611 B，固定 release 签名；CI `Verify release signature` 通过）
  - 下载包校验：`aapt2 dump badging` → versionCode 14 / versionName 0.8.1；sha256 `1e3fa62158e988d027959af5c03f485b4eadf3e4d91a7502c83872ffef3e1bc2`
  - CI（tag）：Android CI run `43835906713` success（单测 → debug → signed release → Release）
  - CI（main）：Android CI run `43835899658` success

## 13. v0.8.2 补丁发布记录（搜索多结果）

- 触发：人工反馈「搜索只会出来一个结果」（如搜「小虎队」）
- 根因：`SearchScreen` 对所有输入先调用 `MusicRepository.resolveInput`；它对 `VideoInput.Keyword` 只取首条视频（展开分 P）返回，关键词分支永远只有 1 条
- 修复：
  - `core/input/InputResolver.kt`（从 data 模块移入 core，纯函数）：新增 `SearchInput.KeywordQuery / DirectVideo` 与 `parseSearchInput()`
  - `SearchScreen`：关键词 → `search()` 列表（20 条/页）；BV/AV/b23.tv/完整链接 → `resolveInput()`，失败回退搜索
  - 回归测试（`data/.../InputResolverTest.kt`）：关键词路由 3 例、直接视频引用路由 4 例
- 验证：
  - 线上接口实测：`/x/web-interface/search/type?keyword=小虎队` → `code:0`，`result` 20 条，`numPages:50`
  - `:data:testDebugUnitTest --tests InputResolverTest --rerun-tasks` → 12 tests / 0 failures
  - 全量门禁（app assembleDebug+assembleRelease、app/data/playback/lyrics 单测 --rerun-tasks）→ 333 tasks / exit 0
  - 说明：发布时开发机 adb 因 USB 枚举错误（`can't set config #1, error -32`）无法真机验证，由用户在自己手机上安装验证
- Release：✅ 2026-11-21 — https://github.com/small-ora/bbplayer-android/releases/tag/v0.8.2
  - 资产：`app-release.apk`（4,454,611 B，固定 release 签名；CI `Verify release signature` 通过）
  - 下载包校验：`aapt2 dump badging` → versionCode 15 / versionName 0.8.2；sha256 `eba4b527e8a57ea82343005fa25af1ab8b261ce2cc32062ef229c1534fe81272`
  - CI（tag）：Android CI run `44915912303` success（单测 → debug → signed release → GitHub Release）
  - CI（main）：Android CI run `44915909860` success
  - 真机验证：开发机 adb USB 故障未能验证，由用户安装 0.8.2 在自有手机上验收
