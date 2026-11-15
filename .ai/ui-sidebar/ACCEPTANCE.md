# 验收包 — 主导航迁到左上侧边栏 + 底栏音乐播放控件（含 v5 合并验证）

> **级别**：L2（多文件 UI 重构 + 需要产品取舍；不涉鉴权/schema/依赖/CI）
> **已执行阶段**：0 勘察 → 1 隔离分支 → 2 SPEC 冻结 → 3 PLAN → 4 实现 → 5 自检 → 6 独立审查（FAIL→修复→复验）→ 7 验收包
> **跳过阶段**：8 合入（main 未改动，等你授权）
> **状态**：**已合入 main（用户授权后再 fast-forward，main = d72ffe4，与分支树完全一致）**；独立审查 + 修复复核 PASS；全量门禁 455 单测 0 失败

## 0. 一句话结果

手机主界面底部的「导航图标」已移除，改为左上角汉堡打开的**左侧抽屉**（首页/搜索/音乐库/我的 + 排行榜/收听历史/已下载/导入歌单/设置）；
底栏变成**纯音乐播放控件**（封面/标题/歌手/进度线/上一首/播放暂停/下一首/队列，参考椒盐音乐）；
v5 登录体系已合并进本分支（**两次合并均 0 冲突**），合并后 455 个单测 0 失败，实机验证抽屉/登录页/播放条/遮罩与返回键关闭均正常。

## 1. 交付物与基线

| 项 | 值 |
|---|---|
| 分支 | `feat/ui-sidebar-playerbar` |
| worktree | `/root/bbplayer/.agents/worktrees/feat/ui-sidebar-playerbar` |
| HEAD | `d72ffe4`（含 2 个 merge commit） |
| SPEC 冻结 | `b8af501`（`.ai/ui-sidebar/SPEC.md`） |
| 实现提交 | `70db157`（导航/底栏重构）、`380750d`（播放条并入遮罩下层）、`65c3f4f`（独立审查 P1/P2 修复） |
| 合并提交 | `e29404d`（v5 功能）、`d72ffe4`（v5 最终文档） |
| main 状态 | **已合入**：用户授权后 `git merge --ff-only feat/ui-sidebar-playerbar`，代码 = `d72ffe4`（与分支树一致），随后 `4f89a6c` 只追加本验收文档（`git diff d72ffe4..main` 仅 .ai/ui-sidebar/*.md） |
| DIFF_CMD | `git diff e71c9bd..65c3f4f`（仅本任务改动）；合并后范围 `git diff main..HEAD` |
| LOG_CMD | `git log --oneline e71c9bd..HEAD` |
| ENFORCEMENT | `prompt-only`（仓库无 pre-commit / CI required check）；无远端 push |
| SPEC 是否经人确认 | 否（方向由你给，SPEC 由我起草；如有异议按「修改后通过」） |

## 2. 改动清单（相对 `main` 的全部差异，共 7 个文件）

| 文件 | 为什么 |
|---|---|
| `ui/components/NavDrawer.kt`（新增） | `LocalOpenMenu` + Salt 风格抽屉（品牌头部 + 导航 4 项 + 更多 5 项），头部预留 `[MERGE-HOOK:v5-account]` 账号槽位；用带 `drawerState` 的 `ModalDrawerSheet` 以支持返回键关闭 |
| `ui/components/BottomPlayerBar.kt`（新增） | 底栏音乐控件：贴底条（DOCK）/悬浮胶囊（FLOAT）、封面/标题/歌手/2dp 进度线/上一首/播放暂停/下一首/队列；缓冲态也保留播放/暂停无障碍语义 |
| `ui/components/Headers.kt` | `LargeTitle` 从 `CompositionLocal` 取菜单回调，自动在**左上角**显示汉堡；宽屏无抽屉时自动隐藏 |
| `ui/AppRoot.kt` | 手机端 `ModalNavigationDrawer` 包裹内容 + 播放条；宽屏保留 `NavRail`；遮罩/返回键可关抽屉；`LocalOpenMenu` 回调缓存；路由表/`NavHost` 未改 |
| `ui/components/BottomDock.kt`（删除） | 底栏导航图标已迁走 |
| `ui/components/MiniPlayer.kt`（删除） | 被 `BottomPlayerBar` 取代 |
| `.ai/ui-sidebar/SPEC.md` | 本任务冻结 SPEC（PLAN/recon/REVIEW/ACCEPTANCE/evidence 未入库） |

> `git diff --stat main..HEAD` 只有上表 7 个文件 → **v5 的代码没有被本任务修改**，这是"可完全合并"的机器可验证证据。

## 3. AC 逐条对照与证据

| # | AC | 结果 | 证据 |
|---|---|---|---|
| AC1 | 窄屏四个主 tab 左上出现「菜单」，点击滑出左侧抽屉；含 9 入口；当前项高亮；点项后关抽屉并跳转 | PASS | dump `content-desc=菜单`；抽屉 9 项文本齐全；逐项点开 search/library/profile/leaderboard/history/downloads/import/settings 全部 PASS；**点遮罩空白关闭**、**返回键关闭** 均 PASS（`evidence/review-fixes-verified.txt`）；截图 `21-merged-drawer.png`、`02-drawer.png` |
| AC2 | 底栏不再有导航图标；无曲目不显示；播放后出现播放条（封面/标题/歌手/进度线/上一首/播放暂停/下一首/队列）；点条体进播放页；按钮生效 | PASS | dump：底栏导航 desc 为空集、底栏 desc=上一首/播放/下一首/播放队列；点播放→「暂停」toggle 生效；下一首切歌；点歌单/条体进播放页；缓冲态仍有「播放/暂停」语义（修复后）；截图 `20-merged-home.png` |
| AC3 | 宽屏 ≥600dp：常驻左侧导航、无汉堡；底部悬浮胶囊 | PASS | `wm size 1600x2560 + density 320`（800×1280dp）：dump desc=首页/搜索/音乐库/我的 常驻、`菜单` 不存在、播放条仍在；已恢复原分辨率；`09-wide-rail.png` |
| AC4 | 设置「底部停靠/悬浮胶囊」仍生效 | PASS | FLOAT：四周圆角+两侧边距胶囊（`08-float-capsule.png`）；DOCK：贴底条（`01-home.png`） |
| AC5 | 既有页面打开/返回无崩溃、无回归 | PASS | 逐页冒烟：播放页/设置/歌单/下载/历史/排行/导入；`adb logcat` FATAL=0；播放页渲染正常（`13-player.png`） |
| AC6 | 与 v5 登录分支可完全合并 | PASS（已实际合并 2 次） | `git merge main` 两次均 0 冲突；合并后 `assembleDebug + app/data/playback 单测 = 455 tests / 0 fail / 0 skip / exit 0`；冲突标记 0；`main` 为本分支祖先；`evidence/merge-evidence.txt`、`merge-rehearsal.log`、`final-merged-gate.log` |
| 集成 | 合并后登录/账号入口可用 | PASS | 抽屉→我的→v5 账号卡→登录页（扫码/Cookie）正常返回；`22-merged-profile.png`、`23-merged-login.png`；FATAL=0 |

## 4. 独立审查

- 审查者：fresh subagent `39e1a7d7…`（未参与实现、只读），依据冻结 SPEC `b8af501`，对象 `e71c9bd..380750d` + 合并结果。
- 首轮：**FAIL** — 2×P1（遮罩点击被 `gesturesEnabled=false` 禁用；返回键不关抽屉）+ 2×P2（缓冲态播放语义丢失；`LocalOpenMenu` 每帧新建 lambda）+ 1 文档缺口。
- 处置：全部 **FIXED** 于 `65c3f4f`，逐条设备复验 PASS（点遮罩关、返回键关、播放语义、回调缓存）；文档缺口已补 `merge-rehearsal.log`。
- 修复后门禁：**BUILD SUCCESSFUL / exit 0，455 tests / 0 fail / 0 skip**。
- 复核：fresh 复核者 `069a2573…` 只审修复 hunk（对照 `e29404d`），逐条字节码级确认 P1-1/P1-2/P2-3/P2-4 全部 FIXED、无回归、无新问题，**REVIEW2_RESULT: PASS**（`.ai/ui-sidebar/REVIEW.md`）。
- 审查者点名清单：无测试/断言/skip/CI 配置改动；无 lockfile/依赖变更；SPEC 未被改弱。

## 5. 风险与回滚

- **回滚**：`git branch -D feat/ui-sidebar-playerbar`（含 worktree）即可完全撤销；**main 未改动**，v5 不受影响。已合入后回滚：`git revert -m 1 <merge>` 或 reset 到 `9eea37d`（需授权）。
- 抽屉交互：开态允许滑动/遮罩点击/返回键关闭；关态禁用手势，避免拦截横向列表/进度条拖拽（取舍已记录）。
- 设备证据时间 18:20–18:38，APK 由最终 HEAD 构建；设备与另一会话共享，若其重新安装 APK 以对方为准。
- 已知限制：抽屉只从主 tab 页打开（详情页保持返回箭头）；详情页不显示底栏（改造前同样不显示）。
- 安装注意：本机 `adb install` 流式安装会挂起，需 `adb install --no-streaming`（与代码无关）。

## 6. 我替你做的判断与假设（可否决）

1. **左上而非右上**：椒盐音乐截图里汉堡在标题左侧；改右侧只是抽屉方向 + 按钮位置两处小改。
2. **抽屉形态**：手机 = 左侧抽屉；宽屏 = 现成常驻左侧 rail。
3. **DOCK/FLOAT 语义迁移**：DOCK=贴底条、FLOAT=悬浮胶囊；**未改默认值**（DOCK）。
4. **底栏只在主 tab 显示**（与改造前一致），详情页/设置页不显示。
5. **没把账号入口放进抽屉**：v5 账号卡在「我的」页，抽屉头部留 `[MERGE-HOOK]` 槽位；v5 A1「登录后导航栏显示头像昵称」是否要挪到抽屉头部，是产品取舍，等你定。
6. **没有合入 main、没 push**（按流程等授权）。

## 7. 需要你验收的 3 件事（≤10 分钟）

1. **看图**：`evidence/21-merged-drawer.png`（抽屉）、`evidence/20-merged-home.png`（底栏）、`evidence/08-float-capsule.png`（悬浮胶囊）、`evidence/22-merged-profile.png`（合并后账号卡）。
2. **上手 3 分钟**：点左上菜单 → 点空白/返回键关抽屉 → 各页切换 → 底栏播放/暂停/上/下一首 → 设置切换迷你播放器样式。
3. **确认合入结果**：你已授权，main 已 fast-forward 到 `d72ffe4`（与分支树一致），main 工作区干净；
   复核证据 `evidence/main-checkout-ff-gate.log`。如需回退：`git reset --hard 9eea37d`（需再授权）。

## 8. 明确未做

- 未 push / 未开 PR / 未改 CI / 未改 main。
- 未做：把账号头像放进抽屉头部、详情页全局播放条、手势切歌/上滑展开、迷你条歌词行（均为 SPEC 非目标）。
- 未做深色主题逐页截图（主题切换由既有代码保证，本任务未验证）。

## 9. 结论（请你勾选）

- [ ] 保留检查点，稍后再合入
- [x] 授权合入 main（已执行 ff-only merge，main = d72ffe4）
- [ ] 修改后通过：需要修改 ______________
- [ ] 打回：原因 ______________
- [ ] 无法验收

## 附：证据索引（`.ai/ui-sidebar/evidence/`）

- `merge-evidence.txt` / `merge-rehearsal.log`：两次合并、0 冲突、main 为祖先、差异仅 7 文件
- `final-merged-gate.log` / `full-merged-after-review-fixes.log` / `full-branch.log`：全量门禁 exit 0（455 tests）
- `main-checkout-ff-gate.log`：合入 main 后主 checkout 的全量门禁（最终确认）
- `review-fixes-verified.txt`：P1/P2 设备复验（遮罩/返回键/播放语义）
- 截图：`01-home.png`、`02/04/21-drawer*.png`、`08-float-capsule.png`、`09-wide-rail.png`、`10-search.png`、`11-library.png`、`12-profile.png`、`13-player.png`、`20-merged-home.png`、`22-merged-profile.png`、`23-merged-login.png`
- dump：`01-home.xml`、`02-drawer.xml`、`04-drawer-v2.xml`、`05-leaderboard.xml`、`06-transport.xml`、`09-wide-rail.xml`、`13-player.xml`
- 审查：`../REVIEW.md`
