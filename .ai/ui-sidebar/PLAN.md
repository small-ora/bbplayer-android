# PLAN — 侧边栏导航 + 底栏播放条

对应 SPEC：`.ai/ui-sidebar/SPEC.md` · 级别 L2 · BASE `e71c9bd`

## 写范围（与 v5 登录会话严格不重叠）

| 文件 | 动作 | 说明 |
|---|---|---|
| `app/src/main/java/com/bbplayer/android/ui/components/NavDrawer.kt` | 新增 | `LocalOpenMenu` CompositionLocal + `AppDrawerSheet` + 条目/头部（含 `[MERGE-HOOK]` 账号槽位注释） |
| `app/src/main/java/com/bbplayer/android/ui/components/BottomPlayerBar.kt` | 新增 | 底栏音乐控件：贴底/悬浮两态、封面/标题/歌手/进度线/上一首/播放暂停/下一首/队列 |
| `app/src/main/java/com/bbplayer/android/ui/components/Headers.kt` | 修改 | `LargeTitle` 读 `LocalOpenMenu`，非空时左侧显示「菜单」汉堡（不改调用方） |
| `app/src/main/java/com/bbplayer/android/ui/AppRoot.kt` | 修改 | 窄屏包裹 `ModalNavigationDrawer` + `CompositionLocalProvider`；底栏由 `BottomDock` 换 `BottomPlayerBar`；宽屏保留 `NavRail`。**不触碰** `Routes`/`NavHost` 路由注册区 |
| `app/src/main/java/com/bbplayer/android/ui/components/BottomDock.kt` | 删除 | 底栏导航图标已迁走 |
| `app/src/main/java/com/bbplayer/android/ui/components/MiniPlayer.kt` | 删除 | 被 `BottomPlayerBar` 取代（唯一调用方是 AppRoot；`sp2()` 无外部引用） |

不写：`ProfileScreen`/`LibraryScreen`/`SettingsScreen`/`PlayerScreen`/`RealAppGraph`/`data/**`/`core/**`。

## 步骤

1. **S1 组件**：新增 `NavDrawer.kt`、`BottomPlayerBar.kt`。
   - 验证：`./gradlew :app:compileDebugKotlin`；预期 exit 0。
   - 回滚点：commit（未接线，不影响行为）。
2. **S2 接线**：`Headers.kt` 汉堡；`AppRoot.kt` 抽屉 + 播放条 + 删除旧组件。
   - 验证：QUICK exit 0；真机安装后 dump 断言 `菜单` 存在、底栏无导航图标。
   - 回滚点：commit。
3. **S3 真机验证 + 截图**：AC1/AC2/AC4（窄屏）+ AC3（改 wm size 模拟宽屏）+ AC5 冒烟/FATAL。
   - 证据：`.ai/ui-sidebar/evidence/`（截图 + dump 文本 + 日志）。
4. **S4 全量门禁**：`./gradlew :app:assembleDebug :app:testDebugUnitTest`（FULL）。
5. **S5 合并预演**：`git merge-tree --write-tree HEAD main`（以及若对方已有新提交则重跑），
   记录冲突文件与解决策略；若对方已提交且可编译，则在临时 worktree 真合并 + 构建 + 单测。
6. **S6 独立审查**：fresh subagent 按 SPEC + diff 审查（只报正确性/回归/AC 问题），P0/P1 处置。
7. **S7 验收包**：`.ai/ui-sidebar/ACCEPTANCE.md`（级别/阶段/证据/风险/回滚/需人验收的 3 件事）。

## 每步验证命令与预期证据

- QUICK: `./gradlew :app:compileDebugKotlin` → `BUILD SUCCESSFUL`
- FULL: `./gradlew :app:assembleDebug :app:testDebugUnitTest` → `BUILD SUCCESSFUL`（测试数量与 v4 基线一致，无 skipped）
- DEVICE:
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  - `adb shell am start -n com.bbplayer.android.debug/com.bbplayer.android.MainActivity`
  - `python3 .ai/scripts/ui.py dump` → 断言 `content-desc="菜单"`
  - `python3 .ai/scripts/ui.py tap desc 菜单` → 截图 `.ai/ui-sidebar/evidence/drawer-open.png`
  - 点「排行榜」→ dump/截图断言到达排行榜且抽屉关闭
  - 播放后截图 `bottom-player-dock.png` / 设置 FLOAT 后 `bottom-player-float.png`
  - 宽屏：`adb shell wm size 1600x2560; adb shell wm density 320` → 重启 Activity → 截图 `wide-rail.png`；恢复原尺寸
  - `adb logcat -d | grep -E "FATAL EXCEPTION"` → 空

## 重分级触发

- 若需要改 `core/**` 契约、`SettingsScreen`、或新增依赖 → 停下回 SPEC。
- 若与 v5 会话写范围出现新的重叠文件（AppRoot 以外）→ 停下重新划分写范围。

## 执行记录

- S1 组件：`NavDrawer.kt` + `BottomPlayerBar.kt` 新增；`./gradlew :app:compileDebugKotlin` exit 0（18:00）。
- S2 接线：`Headers.kt` 汉堡（CompositionLocal，零 screen 改动）、`AppRoot.kt` 抽屉/播放条、删除
  `BottomDock.kt`/`MiniPlayer.kt`；编译 exit 0；实机首版验证后发现「抽屉打开时底栏盖在遮罩上」，
  追加修复：播放条移入抽屉内容层（commit `380750d`），复验通过。
- S3 真机验证（MI 6 / 1080×1920@480dpi=360dp）：
  - AC1 PASS：home dump `content-desc=菜单`；抽屉 9 项全列；点项后抽屉关闭并跳转（search/library/profile/
    leaderboard/history/downloads/import/settings 全部 PASS）。
  - AC2 PASS：底栏无导航图标（`BOTTOM_NAV_DESC=[]`）；底栏 dump 有 上一首/播放/下一首/播放队列；
    点播放→`暂停`（toggle 生效）；下一首切换曲目；点条体/歌单进入播放页正常。
  - AC4 PASS：设置→迷你播放器→悬浮胶囊后，底栏变四周圆角带边距胶囊；DOCK 为贴底条。
  - AC3 PASS：`wm size 1600x2560; density 320`（800×1280dp）→ 常驻 rail（desc 首页/搜索/音乐库/我的），
    `菜单` 不存在，底部胶囊仍在；已恢复原分辨率。
  - AC5 PASS：逐页冒烟无 FATAL（`FATAL_COUNT=0`）；播放页渲染正常。
  - 备注：安装须用 `adb install --no-streaming`（本机 streaming 安装会挂起，与改动无关）。
- S4 全量门禁（本分支）：`assembleDebug + testDebugUnitTest` exit 0。
- S5 合并：v5 登录会话已在 main 完成（`2e2c6af`，工作区 clean）；`git merge main` **0 冲突**
  （仅 `AppRoot.kt` 有双方改动，git 自动合并）。合并后全量门禁：`assembleDebug + app/data/playback
  单测` = **455 tests / 0 fail / 0 skip**，exit 0；冲突标记扫描 0。证据 `evidence/merge-evidence.txt`。
- S6 独立审查：fresh subagent（见 REVIEW.md）。
- S7 验收包：`ACCEPTANCE.md`。
