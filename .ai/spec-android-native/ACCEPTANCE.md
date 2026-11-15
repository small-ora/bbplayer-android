# 验收包 v1 — BBPlayer 安卓原生版

**级别**：L2/L3（新建跨模块应用；无鉴权/密钥红线，按 L2+ 执行）
**已执行阶段**：0 勘察 → 1 隔离基线 → 2 SPEC（冻结 b7a08e9）→ 3 PLAN → 4 实现（4 个写入者并行）→ 5 自检 → 6 独立审查（fresh subagent）→ 7 人工验收（本文件）
**跳过阶段**：8 合入（未授权 push/PR/部署，仅本地提交）
**状态**：**本地可运行、可安装；等待人工验收**（非 BLOCKED）

`SPEC_BASE` = `b7a08e9`（`.ai/spec-android-native/SPEC_BASE`）· `HEAD` 见 `git log --oneline -1` · `LOG_CMD` = `git log --oneline b7a08e9..HEAD` · `DIFF_CMD` = `git diff b7a08e9..HEAD`
`ENFORCEMENT`：prompt-only（无 CI / pre-commit）· 有 git · 无远端 · 未 push

---

## 1. 一句话结果

用 Kotlin + Compose 原生重写了 BBPlayer 的核心听歌链路（B 站匿名搜索→播放→歌词→歌单/收藏/历史→统计），UI 基于椒盐音乐开源组件库 SaltUI 的设计 token 与组件实现；**已在真机上跑通真实 B 站音频播放、后台通知、歌词在线获取、断点续播与主题切换**，165 个单元测试全绿，构建可复现。v1 不含账号登录（按用户拍板）。

## 2. 改动清单（按主题）

| 主题 | 内容 |
| --- | --- |
| 工程 | 多模块 Gradle：`:app/:core/:data/:playback/:lyrics`；AGP 8.13.2 + Kotlin 2.4.21 + Compose 1.10 + SaltUI 2.8.6（LGPL-2.1）+ Media3 1.11.1 + Coil 3.4.0 |
| 核心契约 | `core/model/Models.kt`（Track/AudioStream/Lyrics/Playlist/PlaybackState/PlayMode…）、`core/contracts`（6 个仓储/播放器接口 + AppException） |
| 数据层 | B 站接口（finger/spi 取 buvid3、search/type、view、pagelist、playurl、ranking/v2、related、b23 重定向解析）、BV/AV/URL 解析、本地 JSON 原子持久化（歌单/收藏/历史/续播）、DataStore 设置 |
| 播放层 | ExoPlayer + MediaSessionService + 媒体通知（封面/上一首/播放/下一首）、队列/播放模式/倍速/定时关闭、`bbplayer://` 惰性流解析 + 403 重解析重试、启动恢复 |
| 歌词层 | LRC + 增强 LRC（词级）、offset、翻译合并、QQ 音乐→网易云匹配与获取、sha1 文件缓存、候选搜索、手动粘贴 |
| UI | Salt 风格设计系统（颜色/圆角/字级/半透明卡片）、浮动底部 dock（胶囊迷你播放器 + 4 图标 tab）、首页/搜索/音乐库/我的/设置/播放器/歌词/歌单详情/队列 sheet、动态取色、封面取色背景 |
| 文档 | `README.md`、`NOTICE`（SaltUI LGPL 与 BBPlayer MIT 归属）、`.ai/recon/*`（上游功能/设计勘察）、SPEC/PLAN/本验收包 |

## 3. AC 逐条对照（证据见 `.ai/evidence/`）

| AC | 结果 | 验证方式 / 证据 |
| --- | --- | --- |
| A1 assembleDebug exit 0 | ✅ | `./gradlew :app:assembleDebug` exit 0；APK `app/build/outputs/apk/debug/app-debug.apk`（见 final-gate.log） |
| A2 testDebugUnitTest ≥25 用例 | ✅ | **165 个用例全绿**：data 52、playback 49、lyrics 64（`--rerun-tasks`，final-gate.log） |
| A3 安装+冷启动无 FATAL | ✅ | `pm install` Success；`am start` 后截图；logcat 无 `FATAL EXCEPTION` |
| B1 首页最近播放 + B 站热门 | ✅ | 截图 `shots/13-restore.png`（真实榜单/最近播放）；刷新按钮可用；网络错误态代码路径存在 |
| B2 搜索关键词/BV/AV/b23/URL | ✅（BV 真机、其余单测） | 真机输入 `BV1auGwzMEs1` → 结果行（`shots/f2-search`）；BV/AV/URL/b23 解析 10 个单测（data） |
| B3 结果字段 + 搜索历史 | ✅ | 真机截图（封面/标题/UP/时长/播放量 + 最近搜索 chip + 清空） |
| B4 解析单测 | ✅ | `InputResolverTest`(10) / `BiliModelsTest`(14) |
| C1 播放推进 | ✅ | 真机真实播放：播放页 00:04/00:14 推进；media_session `state=PLAYING(3) position>0` |
| C2 后台播放 + 通知 + 锁屏 | ✅ | 真机 HOME 后 session 仍 PLAYING；通知栏截图 `shots/12-notification.png`（封面+标题+控制）；`PlaybackService isForeground=true` |
| C3 播放模式 | ✅（单测+UI） | QueueNavigator 12 用例覆盖 4 种模式/边界；播放页循环图标切换 |
| C4 队列 + 断点续播 | ✅ | 真机 force-stop 后重启：迷你播放器恢复曲目与进度（截图 `shots/13-restore.png`），state 文件 `playback_state.json` 存在 |
| C5 seek + 倍速 | ✅（单测+UI） | SeekLine 手势 + 倍速弹窗；`PlaybackMappingTest` |
| C6 定时关闭 | ✅（单测+UI） | `SleepTimerTest`(5) 到期暂停逻辑；播放页定时弹窗 |
| C7 音质档位+回退 | ✅（单测+UI） | 设置页三档；`BiliModelsTest` 选流/回退用例；真实 playurl 返回 30216/30232/30280 |
| C8 403 自动重解析 | ✅（单测+代码） | `StreamRetryPolicyTest`(4)；`BbDataSourceFactory` 重试路径，日志 TAG=BBPlayback |
| D1 LRC 解析 | ✅ | `LrcParserTest`(26)：多时间戳/offset/毫秒/百分秒/畸形行/排序去重 |
| D2 在线歌词 + 手动粘贴 | ✅ | 真机歌词页显示真实歌词（`shots/17`）；QQ/网易云实测 200（`.ai/evidence/lyrics-live.md`）；粘贴对话框 + 手动搜索对话框已实现 |
| D3 逐行高亮/自动滚动/点击 seek | ✅ | 真机截图（当前行加粗放大、居中）；点击行 seek 实现；用户滚动暂停跟随 + 2.5s 恢复 |
| D4 偏移 ±0.5s + 持久化 | ✅ | 真机 +0.1s×2 后退出重进仍显示 `偏移 200 ms`（`shots/17-lyrics-offset.png`） |
| D5 匹配清洗单测 | ✅ | `TitleCleanerTest`(12) / `LyricMatcherTest`(7) |
| E1 歌单 CRUD + 加歌 | ✅ | 真机新建「Sleep」歌单（`shots/14`）；播放页「添加到歌单」成功（`shots/15`） |
| E2 最近播放记录 | ✅ | 真机首页「最近播放」横滑（`shots/13`，来自真实历史） |
| E3 本地点赞 | ✅ | 真机播放页红心 → 音乐库收藏列表出现（`shots/18-favorites.png`） |
| E4 统计 + 热力图 | ✅ | 真机「我的」显示累计 1 分钟 / 3 次 + 12 周热力格（`shots/07-profile.png`） |
| F1 Salt 设计 token | ✅ | 颜色/圆角/字级对齐 SaltUI；卡片半透明+发丝描边；无 M3 紫色 | 
| F2 深浅色 + 动态取色 | ✅ | 真机深色截图（`shots/09-settings-dark.png`、`11-profile-dark.png`），Monet 主导色生效 |
| F3 播放页椒盐风 | ✅ | 封面取色模糊背景+白字、大圆角封面、2dp 进度线+下方时间、裸图标控制行、底部功能行（`shots/15`） |
| F4 迷你播放条 | ✅ | dock 内胶囊迷你条（`shots/13`），点击进播放页、队列入口可用 |
| F5 底部导航 | ✅ | 浮动 dock + 4 图标 tab（首页/搜索/音乐库/我的），切换保留状态 |
| F6 空/加载/错误态 | ✅ | 收藏空态（`shots/18`）、歌词失败态（重试/手动搜索，`shots/17`）、首页 loading/error |
| G1 设置项持久化 | ✅ | 真机主题切换持久化；音质/自动播放/续播/字号/偏移均写 DataStore |
| G2 清缓存 + 关于/归属 | ✅ | 设置页「清除歌词缓存」+ 关于区（版本/BBPlayer MIT/SaltUI LGPL-2.1） |
| G3 我的入口可达 | ✅ | 截图 |
| H1 断网可重试不崩溃 | ⚠️ 代码路径已实现，未在共享真机上做断网演练（避免影响并行设备任务）；建议人工验收时飞行模式点一次 |
| H2 连续使用无崩溃/ANR | ✅（20+ 分钟真机操作） | 搜索/播放/歌词/队列/设置/主题反复操作，logcat 无 FATAL |
| H3 无账号/无上传/无密钥 | ✅ | 代码审查：无 Cookie/SESSDATA/登录接口；仅只读公开接口；无分析 SDK |

## 4. 独立审查（Phase 6）

- 审查者：**fresh subagent**（未参与实现，未看过实现过程）· 审查对象：`b7a08e9..HEAD` · 依据：冻结 SPEC
- 结论与 P0/P1 处置：见 `.ai/spec-android-native/REVIEW.md`（**本包等待该报告回填后定稿**）
- 已知由 UI 只读审查（lyrics-dev）提前发现并已修复的问题：历史列表重复 key 崩溃（P0）、搜索重试卡加载、歌词自动滚动被误判为用户滚动、歌词偏移不持久化、刷新歌词误清全部缓存、`indexOf` 重复曲目播错、seek 滑块 px/dp 混用、封面取色全尺寸解码、协程裸 launch 崩溃风险。

## 5. 风险与回滚

- **回滚**：`git reset --hard b7a08e9`（回到 SPEC 基线）；或 `git checkout <上一提交> -- <路径>`。APK 回滚 = 重新安装旧包。
- **许可风险**：SaltUI 2.8.6 为 LGPL-2.1（Gradle 依赖、未修改；应用源码全量提供，满足重链接要求）。若要对外分发，建议人工确认或改用 Apache-2.0 的 SaltUI 3.0（需 compileSdk 37/AGP 9.1，本机 SDK 仅 36）。
- **接口风险**：未实现 WBI 签名；若 B 站对匿名 search/playurl 收紧（-412/风控），搜索/播放会失败，需补 WBI（上游有实现）。
- **数据风险**：搜索/榜单结果 cid=0 时播放依赖 pagelist 兜底（多 P 视频默认 P1）。
- **设备环境**：真机时钟曾被关闭自动同步（导致 TLS 全部失败），已通过 `settings put global auto_time 1` 修正；验收若换设备请确认系统时间正确。

## 6. 我替你做的判断与假设

1. “安卓原生开发语言”按 **Kotlin**（官方推荐、Compose 生态）执行；未选 Java。
2. v1 不做账号登录（你已拍板），因此收藏夹/合集/高音质/下载/导出均不做；收藏 = 本地收藏。
3. 歌词链路用 **QQ 音乐 → 网易云**，未做 B 站字幕回退（与 SPEC D2 措辞略有差异，按任务书优先级实现）。
4. 用 **SaltUI 2.8.6**（LGPL）而非 SaltUI 3.0（Apache），原因是本机 Android SDK 只有 platform 36，而 3.0 要求 compileSdk 37 + AGP 9.1；如需 Apache 许可版本，升级工具链即可。
5. 设备是共享的（`adb devices` 在另一个并行任务的 MI 6 上），我修正了该机系统时间以让网络可用；未改动其他 App。
6. 未 push/未开 PR/未部署（按你的全局约定）。

## 7. 需要你验收的 3 件事（≤10 分钟）

1. **装到手机上听一首歌**：`adb install -r app/build/outputs/apk/debug/app-debug.apk` → 首页点任意热门 → 确认出声、播放页进度推进、按 Home 后通知可控制。
2. **看风格**：对比 `.ai/evidence/shots/` 下播放器/歌词/首页截图与椒盐音乐，判断视觉是否“对味”（这是本次主观验收点）。
3. **点一遍关键路径**：搜索 BV → 播放 → 歌词 → 红心 → 音乐库收藏 → 设置切深色/动态取色；若断网再点一次看错误态（H1 未做真机断网演练）。

## 8. 明确未做

- 登录/收藏夹/合集/UP 主/稍后再看/评论/下载与导出/歌单导入/B 站装扮/桌面歌词/状态栏歌词/播客字幕/平板两栏/数据上报
- WBI 签名、B 站字幕歌词回退、酷狗歌词
- 未配置 CI；未做 release 签名与上架

## 9. 结论（由人填写）

- [ ] 通过，可保留当前检查点
- [ ] 授权 push / 开 PR（目标仓库：____）
- [ ] 修改后通过（意见：____）
- [ ] 打回
- [ ] 无法验收（原因：____）
