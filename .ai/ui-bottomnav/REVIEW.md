# REVIEW — 独立审查记录与处置

## 审查独立性

| 项 | 值 |
|---|---|
| 级别 | L2（SPEC 落盘 + 隔离分支 + fresh reviewer） |
| 审查者 1 | fresh subagent `04c2cc88-7d44-4e0c-bd18-e4233b8c3453`（spawn，未参与实现） |
| 审查者 2 | fresh workflow agent `ui-bottomnav-independent-review`（独立会话，未参与实现） |
| 审查对象 | `git diff 7d5f18d..HEAD -- app/src/main/java`（审查时 HEAD `039b972`；最终修正在此之后） |
| 审查依据 | `git show 7d5f18d:.ai/ui-bottomnav/SPEC.md`（冻结版）；SPEC 校验：`git diff 7d5f18d..HEAD -- .ai/ui-bottomnav/SPEC.md` 为空 |
| 实现过程可见性 | 否（fresh）；审查者只读、未改文件、未 commit、未 adb |

## Findings 与处置

| # | 级别 | 位置 | 问题 | 处置 |
|---|---|---|---|---|
| 1 | P1 | `theme/DesignTokens.kt` + `AppRoot.kt` | 根页 contentPadding 156dp 未计入 `navigationBars` inset（底栏实际 chrome = 150 + inset），AOSP 手势 24dp / 三键 48dp 设备最后一项会被迷你播放器压住 | **已修**：新增 `bbRootContentBottomPadding()` = chrome 常量（80+64+6+6）+ `WindowInsets.navigationBars` bottom inset；Home/Library/Profile 三页改用；chrome 数值改由 `BottomNavHeight/MiniPlayerHeight/...` 推导 |
| 2 | P2 | `screens/login/LoginScreen.kt` | 登录页（二级页）滚动内容无底部 padding，迷你播放器常驻会遮挡 Keystore 提示卡 | **已修**：底部加 `SecondaryContentBottomPadding`（与其余二级页一致） |
| 3 | P2 | `screens/home/HomeScreen.kt` | LazyColumn 去掉 `statusBarsPadding` 后滚动内容绘制到透明状态栏下（证据 09 中「30 首」与状态栏重叠），与曲库/我的不一致 | **已修**：`statusBarsPadding` 恢复到 LazyColumn，header item 内的重复 inset 移除 |
| 4 | P2 | `screens/library/LibraryScreen.kt` | 批量删除无确认，「全选 + 删除」两次点击即永久删除全部歌单 | **已修**：新增 `BatchDeleteConfirmDialog` 二次确认（说明不可撤销），确认后再批量删除 |
| 5 | P2 | `screens/library/LibraryScreen.kt` | `dismissThen` 依赖 `hide().invokeOnCompletion`，弹层中途被移出组合时协程取消 → 动作静默丢失 | **已修**：点击后先 `onDismiss()` 清状态再执行动作；`acting` 防连点保留，动作不再依赖隐藏动画回调 |
| 6 | P2 | `theme/DesignTokens.kt` | 多个 token 未被引用（BottomNavHeight/MiniPlayerHeight/CardShape/bbCardSurface/bbCaptionColor），20sp 双份事实源 | **已修**：删除未用 token/函数；`MiniPlayerHeight`、`RootContentBottomPadding` 由基础尺寸推导并实际参与计算 |
| 7 | P2 | evidence | AC10 截图早于封面 85% 修正；缺纯白主题截图；部分截图早于最后两次提交 | **已修**：最终包重新安装后重拍：播放态/暂停态、发现/曲库/我的/二级页/滚到底/批量确认对话框/纯白主题 |

审查者点名项核对：无断言/期望弱化；无 skip/only/ignore；无测试/CI/lint 配置改动；无快照/lockfile/依赖变更；SPEC 与规则未被改；无鉴权/网络/密钥改动。测试缺失判断：本次无新增 Compose UI 测试（SPEC 非目标禁止新增依赖），新增有状态逻辑（批量删除 ID 集合、sheet 动作）已在真机冒烟覆盖，不列为影响正确性的缺陷。

## 复核结论

- 两位审查者均未发现 P0；发现的 1×P1 + 5×P2（去重后）已全部修复或转为证据补齐。
- 修复后重新执行全量门禁（`:app:assembleDebug` + app/data/playback/lyrics 单测 `--rerun-tasks`）与真机冒烟，见 `evidence/final-gate.log`、`evidence/AC-evidence.md`。

## 第二轮独立审查（fresh workflow agent）追加

第二位审查者（独立会话）复审最终方向后补充/确认：

| # | 级别 | 位置 | 问题 | 处置 |
|---|---|---|---|---|
| 8 | P2 | `screens/login/LoginScreen.kt` | 登录页作为二级页没有底部安全内边距，迷你播放器常驻会遮挡最后的信息卡（AC6 漏项） | **已修**：内容末尾加 `SecondaryContentBottomPadding`。（首版误加在开头，真机截图发现后已移到末尾并重拍 12-login-secondary.png） |
| 9 | P2 | `screens/home/HomeScreen.kt` | LazyColumn 去掉 `statusBarsPadding` 后滚动内容绘制到透明状态栏下；与曲库/我的不一致 | **已修**：`statusBarsPadding` 恢复到 LazyColumn，并追加 `clipToBounds()` 把滚动内容裁在状态栏安全区下方（曲库/我的同步加 `clipToBounds()`）；09-discover-bottom 重拍确认 |
| 10 | P2 | `screens/library/LibraryScreen.kt` | 批量删除无二次确认，全选+删除两次点击即永久删全部歌单 | **已修**：`BatchDeleteConfirmDialog` 二次确认（10b 截图） |
| 11 | P2 | `theme/DesignTokens.kt` | 未使用 token / 20sp 双份事实源；156/140 硬编码 | **已修**：删除未用 token；`MiniPlayerHeight`、`RootContentBottomPadding` 由基础尺寸推导，`bbRootContentBottomPadding()` 动态加系统 inset |
| 12 | 证据 | evidence | AC10 截图早于封面 85% 修正、缺纯白截图、部分截图早于最后提交 | **已修**：最终 APK 重拍 01–12（含播放/暂停态、深色、纯白、登录页、批量确认、滚到底） |

- 审查者点名的 AC 结论：AC1–AC5、AC7–AC12 成立；AC6 因上述两处缺陷一度不成立，修复后由 Lead 在真机 + 源码复核为成立。
- 第二轮审查同时确认：无断言弱化、无 skip/only/ignore、无测试/CI/lint 配置改动、无 lockfile/依赖变更、SPEC 与规则未改、无鉴权/网络/密钥改动。
- 修复后最终门禁（`--no-daemon --rerun-tasks`）exit 0，见 `evidence/final-gate.log`。
