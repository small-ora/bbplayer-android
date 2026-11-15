# 独立审查记录（Phase 6）

## 审查者与依据

| 项 | 值 |
|---|---|
| 审查者 | fresh subagent `39e1a7d7-784d-4f1b-8d86-9e98e16d76ec`（未参与实现、只读、未改文件/未跑构建） |
| 是否看过实现过程 | 否（fresh：只拿到 SPEC + diff + 规则） |
| 审查依据（冻结版本） | `git show b8af501:.ai/ui-sidebar/SPEC.md` |
| 审查对象 | `git diff e71c9bd..380750d` + 合并结果 `git diff main..e29404d` |
| 首轮结论 | **FAIL**（2×P1、2×P2、1 文档缺口） |
| 复核者 | fresh subagent `069a2573-26dc-4adb-bc20-58ecd453e784`，只审修复提交 `65c3f4f`（复核 hunk，不看实现过程） |

## 首轮 findings 与处置

| # | 级别 | 位置 | 问题 | 处置 | 复验证据 |
|---|---|---|---|---|---|
| 1 | P1 | `AppRoot.kt`（原 `gesturesEnabled=false`） | M3 1.4.0 中 scrim 的「点遮罩关闭」被 `gesturesEnabled` 门控 → 抽屉打开后点空白无反应，只能点条目跳转关闭（审查者用 material3 aar 字节码核实 scrim `onClose` 短路） | **FIXED**（`65c3f4f`）：`gesturesEnabled = drawerState.isOpen`（关态不拦截横向拖拽，开态恢复遮罩点击 + 滑动关闭） | 设备复验：点右侧空白 → 抽屉关闭（`evidence/review-fixes-verified.txt` P1-A PASS） |
| 2 | P1 | `NavDrawer.kt` + `AppRoot.kt` | 使用不带 `drawerState` 的 `ModalDrawerSheet` 旧重载 → 不安装 predictive-back，且全仓无 `BackHandler` → 抽屉开着按返回：首页直接退出 App / 其他 tab 在抽屉下出栈 | **FIXED**（`65c3f4f`）：改用 `ModalDrawerSheet(drawerState = drawerState, …)` 重载 | 设备复验：返回键关闭抽屉且仍停留首页（P1-B PASS） |
| 3 | P2 | `BottomPlayerBar.kt` | buffering 时只有无 desc 的 spinner，播放/暂停语义消失（无障碍 + AC2 的 dump 断言抖动） | **FIXED**（`65c3f4f`）：按钮 Box 上加 `semantics { contentDescription = … }`，Icon desc 置空避免重复节点 | dump 复验含「播放」（P2-A PASS） |
| 4 | P2 | `AppRoot.kt` | `LocalOpenMenu provides { … }` 每帧新建 lambda；`staticCompositionLocalOf` 值变会使整棵 nav+drawer 子树失效（播放进度 tick 时反复重组） | **FIXED**（`65c3f4f`）：`remember(drawerState) { … }` 缓存回调 | 代码复核；编译/门禁通过 |
| 5 | 文档 | SPEC AC6 指定的 `evidence/merge-rehearsal.log` 未生成 | 用真合并（0 冲突）+ `git merge-tree` 预演补生成 | **已补** | `evidence/merge-rehearsal.log`、`evidence/merge-evidence.txt` |

## 修复后门禁

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :data:testDebugUnitTest :playback:testDebugUnitTest`
  → **BUILD SUCCESSFUL / exit 0**，**455 tests / 0 failures / 0 errors / 0 skipped**
  （`evidence/final-merged-gate.log`、`evidence/full-merged-after-review-fixes.log`）
- 设备复验（MI 6）：遮罩关闭 / 返回键关闭 / 播放按钮语义 / 抽屉导航 / 底栏控件 / FATAL=0
  （`evidence/review-fixes-verified.txt`）。

## 复核者结论（修复 hunk 复审）

复核者：fresh subagent `069a2573-26dc-4adb-bc20-58ecd453e784`（只读，未看实现过程），对象 `65c3f4f`（对照 `e29404d`）。

- P1-1 **FIXED**：m3 1.4.0 aar 字节码确认 Scrim `onClose` 先判 `gesturesEnabled`，且 tap 处理器仅在 open 时安装；
  `anchoredDraggable(enabled = gesturesEnabled)` → 开态可点遮罩关闭，关态不拦截横向拖拽。
- P1-2 **FIXED**：`ModalDrawerSheet-Snr_uVM(DrawerState, …)` 重载存在，内部 `DrawerPredictiveBackHandler`
  → `PredictiveBackHandler(enabled = drawerState.isOpen)`；编译产物 `NavDrawerKt` 实际调用该重载。
- P2-3 **FIXED**：同一 LayoutNode 的 SemanticsModifier 合并为单个配置，`clickable` + `semantics` 不产生重复节点，
  buffering spinner 的语义并入同一节点。
- P2-4 **FIXED**：`remember(drawerState)` 以 `Composer.changed(drawerState)` 为键复用同一 `() -> Unit` 实例，
  `LocalOpenMenu` 值稳定。
- 回归检查：抽屉开关/切 tab/返回栈/宽屏 rail/播放条链路均未变；未发现新问题。
- 结论：**REVIEW2_RESULT: PASS**。

## 审查者未发现问题的部分（记录）

AC1–AC6 均有实现与证据；合并结果干净（`main..e29404d` 仅本任务 7 文件，合并后 `AppRoot` 的 15 条 `Routes.` 注册与 main 一致，v5 登录/收藏/合集/稍后再看/UP主路由齐全）；底栏确在抽屉遮罩之下（`380750d` 修复正确）；删除组件无残留引用；未发现测试/gradle 配置被削弱；v5 账号卡在 ProfileScreen 仍可从「抽屉→我的」到达（v5 A1 未回退）。
