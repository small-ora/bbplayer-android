# 验收包 v6 — 侧边栏导航 / 底栏播放控件 / 设置项去重 + V6 发布（v0.6.0）

**级别**：L2（多文件 UI 配置重构 + 版本发布；不涉鉴权/schema/依赖）
**状态**：**已实现、独立审查 FAIL→P1/P2 全部修复并复验、构建与单测通过、真机验证通过；随发布提交进入 main 并打 tag v0.6.0**

## 1. 用户需求

> 提交，然后发布 V6 的版本吧，把一些配置项设置一下。有些重复的配置项就只保留一个最方便设置的就行了，其他的就可以取消掉。

## 2. 去重映射（设置项：同一偏好只保留一个入口）

| # | 重复项 | 处理（保留） | 取消 |
|---|---|---|---|
| D1 | 音质 / 高音质 | **「音质」下拉**（选项：Hi-Res 无损、杜比全景声、192K、128K、64K；杜比/无损需登录） | 独立「高音质（登录后可用）」开关 |
| D2 | 导入每批匹配数 | 导入页工具栏「每批 N 首」胶囊（就在使用处、一点即切） | 设置→下载「导入每批匹配数」行与对话框 |
| D3 | 我的页设置入口 | 我的页「设置」 | 我的页「主题与外观」（与「设置」跳同一页） |
| D4 | 启动行为 | 设置→播放「启动时」三选一（关闭 / 恢复上次播放 / 恢复并播放） | 「启动时自动播放」+「断点续播」两个开关 |
| D5 | 缓存清理 | 设置→缓存「清除缓存」（接口缓存 + 歌词缓存，显示用量） | 设置→歌词「清除歌词缓存」 |

保留未动（经核对不算重复）：账号（设置账号区=v5 AC C3；我的账号卡=v5 AC A1）、迷你播放器样式（DOCK/FLOAT 语义不同）、歌词偏移（设置=全局 vs 播放页=逐曲）、播放器背景/主题/动态取色/强调色（不同维度）、导航类入口。

## 3. AC 对照与证据

| # | AC | 结果 | 证据 |
|---|---|---|---|
| AC1 | 设置→播放 只有「音质」一项、无「高音质」开关；选项顺序 无损/杜比/192K/128K/64K；未登录选杜比/无损 → 提示并跳登录；登录后选无损 → 优先 FLAC/杜比（v5 数据层逻辑不变） | PASS（UI/跳转）+ 代码走查 | 真机 dump：`HAS_高音质=False`、`HAS_音质=True`；对话框 dump = `['音质（杜比 / 无损需登录）','Hi-Res 无损','杜比全景声','192K','✓','128K','64K','取消']`；点「Hi-Res 无损」后页面变为登录页（`未扫码`/`打开 Bilibili App 扫描二维码`）；选 AAC 会写 `highQualityAudio=false`，选杜比/无损写 `true`（`SettingsScreen.kt` 走查）；截图 `.ai/evidence/v6/03-quality-dialog.png`、`04-quality-needs-login.png` |
| AC2 | 「启动时」三选一；写两个旧偏好；重进保持 | PASS | 对话框 dump = `['启动时','关闭','恢复上次播放','✓','恢复并播放','取消']`；选「恢复并播放」后行值变为「恢复并播放」；截图 `05-startup-dialog.png` |
| AC3 | 设置页无「导入每批匹配数」；导入页「每批 N 首」仍可用 | PASS | 设置页全量 dump 无「每批」；导入页 dump 有「每批 20 首」；截图 `08-import-batch-pill.png` |
| AC4 | 缓存清理只有一个入口（同时清接口+歌词缓存） | PASS | 设置页 dump：歌词组无「清除歌词缓存」、缓存组有「清除缓存 / 接口缓存 + 歌词缓存」；点按前「9 条 · 427 KB」→ 点按后「1 条 · 188 KB」（清空后访问首页重新产生 1 条），`09-cache-cleared-evidence.png` |
| AC5 | 我的页无「主题与外观」重复入口 | PASS | 我的页 dump `HAS_主题与外观=False`；`07-profile-no-duplicate.png` |
| AC6 | 无回归：debug+release 构建、全模块单测全绿；设置页可滚动/操作 | PASS | `./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :data:testDebugUnitTest :playback:testDebugUnitTest :lyrics:testDebugUnitTest` → **BUILD SUCCESSFUL / exit 0**；单测 **615**（app 2 / data 345 / playback 108 / lyrics 160）0 失败 0 跳过；产物 debug 29 MB、release 4.3 MB（versionCode 7 / versionName 0.6.0，aapt2 校验）；真机设置页滚动/对话框正常 |
| AC7 | V6 发布：versionCode 7 / 0.6.0；README v6 说明；ACCEPTANCE-v6；tag `v0.6.0` 推送触发 CI Release | 见 §5 发布记录 | `app/build.gradle.kts`；README；tag；`git ls-remote` |

## 4. 独立审查

- 审查者：fresh subagent `3841e570-fe42-4da2-8e5f-bd4c1c4cf72b`（未参与实现、只读），依据冻结 SPEC `5045654`，对象 `git diff 5045654..9d97302`（设置去重提交）。
- 首轮结论：**FAIL** —— 1×P1（v5 旧数据「音质=无损/杜比 + 高音质开关未开」时显示与实际不一致）+ 2×P2（登录提示在屏幕外不可见；两次偏好写非原子）。
- 处置：全部 **FIXED** 于 `fc56d18`（见 §7），随后复跑全量门禁（615 单测 / 0 失败）与真机复验。
- 复核：fresh 复核者 `9226e92d-d8a4-4ffc-9e60-80f8adf42cb2` 只审修复 hunk → **REVIEW_V6_FIX_RESULT: PASS**（P1 / P2-1 / P2-2 均 FIXED，无回归）；其指出的 2 个附加 P2 也已一并修复（见 §7）。

## 5. 发布记录（v0.6.0）

| 项 | 值 |
|---|---|
| 版本 | versionCode **7** / versionName **0.6.0** |
| 分支 | `release/v0.6.0` → merge 到 `main` |
| 提交 | `feat(settings): 配置项去重`（9d97302）、`fix(settings): 审查修复`（fc56d18）、`chore(release): v0.6.0`（ec1f6af） |
| Tag | `v0.6.0`（annotated），push `main` + tag → GitHub Actions 自动构建并创建 GitHub Release（附 debug / release APK） |
| 产物 | debug `app/build/outputs/apk/debug/app-debug.apk`（29 MB）；release `app/build/outputs/apk/release/app-release-unsigned.apk`（4.3 MB，未签名） |
| CI 入口 | `.github/workflows/android.yml`（push main / PR / tag `v*`） |

## 6. 风险 / 回滚 / 已知限制

- 回滚：`git revert` 本次设置去重提交；偏好键全部保留，旧值继续生效，备份兼容。
- 音质合并只在 UI 层折叠：`audioQuality` / `highQualityAudio` 两个偏好与数据层选流逻辑均未改，可回退。
- v5→v6 一次性迁移：启动时若发现 `quality=杜比/无损 且 highQualityAudio=false` 会自动补写 `highQualityAudio=true`（偏好键不删、旧备份仍兼容）；未登录时该偏好不生效，界面会标注「需登录」。
- 已知限制（本版未处理，已写入 README）：逐字歌词 / 翻译 / 桌面歌词 / 状态栏歌词 / 自动检查更新暂无独立设置入口（偏好值仍生效）；手机验证码登录、多账号切换未做。
- 发布依赖网络与仓库凭据；若 push 失败将在最终汇报标 BLOCKED 并给出本地 tag/APK 供手动发布。

## 7. 独立审查结论与处置

| # | 级别 | 问题 | 处置（commit `fc56d18`） | 复验 |
|---|---|---|---|---|
| 1 | P1 | v5 两控件独立：`quality=杜比/无损` 但 `highQualityAudio=false` → 界面显示无损、实际取最高 AAC；反向 `highQuality=true + 192K` 也显示与实际不一致 | **已修**：`AppRoot` 在 `LaunchedEffect(Unit)` 做一次性归一化（无损/杜比档且 highQuality=false → 写入 true）；设置页改为按真实行为显示（未登录无损档标「需登录」，highQuality+AAC 显示「无损优先（自动回退 192K）」） | 代码走查 + 全量门禁 + 真机设置页（`10-settings-final.png`） |
| 2 | P2 | 未登录选杜比/无损时，footer toast 在屏幕外且随即跳登录页 → 提示不可见 | **已修**：改用系统 `Toast.makeText(context, …)`，跳转前可见；仍不写偏好、仍跳登录 | 真机截图 `11-quality-prompt-toast.png`（提示 + 登录页） |
| 3 | P2 | `setAudioQuality` 与 `setHighQualityAudio` 两次 DataStore edit 非原子，半途失败会留下不一致态 | **已修**：按「失败时降级方向一致」排序：选无损先写 `highQuality=true` 再写档位；选 AAC 先写档位再 `highQuality=false` | 代码走查 |

- 审查者明确通过的项：启动三选一映射与 `AppRoot` 恢复逻辑一致、缓存合并目录隔离无数据误伤、导入批量仍可用、我的页去重无布局问题、无死代码/未用 import、未改测试与 CI、版本号符合约定。
- 修复后门禁：`assembleDebug + assembleRelease + app/data/playback/lyrics 单测` → **BUILD SUCCESSFUL / exit 0，615 tests / 0 fail / 0 skip**（日志 `.ai/evidence/v6/gate.log`、`/tmp/v6-gate2.log`）。
### 复核结论（fresh `9226e92d…`，只审修复 hunk）：**PASS**

P1 / P2-1 / P2-2 均判定 FIXED，回归检查通过。复核另指出 2 个附加 P2，已一并修复：

| # | 附加 P2 | 处置 |
|---|---|---|
| 4 | 隐私开关「上报播放进度」未登录时仍用 footer toast + 立即跳登录，提示不可见 | 已改用系统 `Toast.makeText(...)`（与音质提示一致），仍不写偏好、仍跳登录 |
| 5 | `highQuality=true + AAC` 的文案「自动回退 128K/64K」不实（数据层固定 flac→dolby→最高 AAC，忽略请求低档） | 文案改为「无损优先（FLAC / 杜比，无则最高 AAC）」 |

修复后最终门禁：`assembleDebug + assembleRelease + 全模块单测` → **BUILD SUCCESSFUL / exit 0，615 tests / 0 fail / 0 skip**（`.ai/evidence/v6/gate3.log`）。

## 8. 证据索引

- 门禁日志：`.ai/evidence/v6/gate.log`（BUILD SUCCESSFUL，615 测试）
- 截图：`.ai/evidence/v6/01-settings-top.png`、`02-settings-scroll0..3.png`、`03-quality-dialog.png`、`04-quality-needs-login.png`、`05-startup-dialog.png`、`06-cache-cleared.png`、`07-profile-no-duplicate.png`、`08-import-batch-pill.png`、`09-cache-cleared-evidence.png`
- SPEC：`.ai/spec-android-native/SPEC-v6.md`
