# SPEC: 搜索结果合集识别为歌单（v1）

- 任务 ID / 链接：用户消息 2026-11-22，示例 https://www.bilibili.com/video/BV1o6k4YUEeX
- 提出人 / 日期：用户 / 2026-11-22
- 级别：**L2**（理由：core 契约 + data 映射 + app UI 跨 3 模块、新增页面与导航、新增单测；人可推翻）
- 状态：draft → 用户已确认关键取舍（2026-11-22，交互/范围/保存行为三问）

## 1. 目标（一句话）

搜索结果的视频如果实际是多 P 分集或 B 站合集（season），点击后不再只播第一首，而是被识别成一个「合集歌单」页面：可播放全部，并可一键保存为本地歌单。

## 2. 验收标准（AC，必须可观察、可验证；实现开始后冻结）

| # | 验收标准（用户可观察的行为/结果） | 验证方式（命令 / 测试名 / 手工步骤） | 状态 |
| --- | --- | --- | --- |
| AC1 | 搜索「小酒馆经典民谣歌单」，点击结果中的 `BV1o6k4YUEeX`（100 P），打开「合集」页面：显示合集标题、`100 首`、以及 P1/P2 曲目列表；**不再直接只播 P1** | 真机：`adb install -r app-debug.apk` → 搜索该关键词 → 点第一条 → `uiautomator dump` 断言包含 `100 首`/`播放全部`/`添加到歌单`；截图 | 未验证 |
| AC2 | 在合集页点「播放全部」→ 播放队列包含全部 100 首，从第 1 首开始；播放页可切到第 2 首 | 真机：点「播放全部」→ 播放页 `uiautomator dump`/截图确认队列数量与当前曲目；点击下一首后当前曲目变为 P2 | 未验证 |
| AC3 | 合集页点「添加到歌单」→ 音乐库新增一个同名本地歌单，包含全部 100 首，可在音乐库打开 | 真机：点「添加到歌单」→ 回音乐库 → `uiautomator dump` 断言歌单名与 `100 首`；截图 | 未验证 |
| AC4 | 再次对同一合集点「添加到歌单」→ **再新建一个**同名歌单（用户选择：每次都新建），两歌单互不影响 | 真机：重复 AC3 一次 → 音乐库出现 2 个同名歌单（或单元测试 + 真机抽查） | 未验证 |
| AC5 | 普通单 P 视频的搜索结果点击行为不变：直接播放，不出现合集页；`getVideoCollection` 对单 P 返回 `null` | `:data:testDebugUnitTest --tests "*BiliVideoCollection*"`（单 P fixture → null）+ 真机点一条单曲结果直接进播放 | 未验证 |
| AC6 | B 站 season 合集（fixture `BV1be41127ft`，26 集）也能识别为合集：映射 26 首、标题/封面/UP 主/时长正确；`ep_count` 超出 view 内 episodes 时用 `seasons_archives_list` 分页补全 | 单元测试：season fixture → kind=SEASON、26 首、字段断言；兜底分页 fixture → 补全数量 | 未验证 |
| AC7 | 边界（失败路径）：点击结果后详情请求失败/超时 → 不崩溃、不空白；提示解析失败并**回退为播放该单曲**（保持旧可用性） | 单元测试（仓储错误传播为 `Result.failure`）+ 真机断网/开飞行模式点击一条结果，观察 Toast 与播放行为 | 未验证 |
| AC8 | 合集页加载失败 → 显示错误态与「重试」；重试成功后正常渲染 | 真机：进合集页前断网/利用无效 bvid 路由触发失败 → 断言错误态；恢复后重试成功（或单元测试 + 代码走查） | 未验证 |
| AC9 | 非目标回归：单曲播放、本地歌单、导入歌单、收藏夹/合集（登录态）等既有测试全绿；`library.json` schema 未变 | `VALIDATION_FULL`（见 recon §3）exit 0；`git diff` 不含数据模型字段删除/改名 | 未验证 |
| AC10 | 搜索结果里点击时会有一次「解析中」反馈（行内 loading），不会误触发多次导航/播放 | 真机快速连点一次结果行 → 只进入一次合集页/只播放一次；截图 | 未验证 |

补充约定（实现细节，不单独占 AC）：
- 合集 = 多 P 视频（`view.pages.size > 1`）或 season 合集（`view.ugc_season` 中 episodes 去空后 > 1）；两者都有时优先 season。
- 合集页「添加到歌单」歌单名 = 合集标题（去 HTML/实体、trim；空则「合集」）。
- 范围仅搜索结果（`SearchScreen`）；首页/排行榜/历史/收藏夹/稍后再看等列表保持现状。

## 3. 非目标（明确不做）

- 不做搜索列表的「合集 N 首」角标预解析（用户拍板：不发预请求）。
- 不覆盖首页热门/排行榜/历史/收藏夹/稍后再看/UP 主页等其他列表的合集点击。
- 不改 `resolveInput`（BV/链接）行为；已能展开多 P，season 链接行为保持现状。
- 不新增/升级依赖，不改 Gradle/CI 配置默认值，不改 `library.json` schema。
- 不做收藏夹（登录态）与本需求的数据源合并；不做合集分页浏览（100 首一次拉全，season 有兜底分页上限）。
- 不做跨设备同步、分享、歌单自动更新（后续 B 站新增 P 不会同步到已保存歌单）。
- 不改搜索列表 UI 视觉（除行内解析中反馈）。

## 4. 约束与现状

- Kotlin 2.4.21 / Compose / SaltUI；`core` 不得依赖 Android，`data` 依赖 `core`，`app` 依赖全部。
- 响应形状与真实 B 站接口一以贯之：映射集中在 `data/.../BiliModels.kt` 纯函数（可单测，不联网）。
- 先例：`BiliCollectionRepository`（登录态合集）、`PagedTracks`（分页）、`PlaylistDetailScreen`（歌单页）、`ImportPlaylistScreen`（createPlaylist + 逐首 addTrack）。
- 验证命令与真机序列见 `.ai/feat-search-collection-playlist/recon.md §3`。
- 沙箱下 Gradle 必须带 `ANDROID_USER_HOME=.gradle/android-user-home GRADLE_USER_HOME=.gradle/user-home`。

## 5. 接口与契约（如涉及）

新增（core，向后兼容，给默认实现以免打断既有测试假实现）：

```kotlin
// core/model/VideoCollection.kt
@Serializable enum class VideoCollectionKind { MULTI_PAGE, SEASON }
@Serializable data class VideoCollection(
    val bvid: String, val title: String,
    val uploader: String = "", val coverUrl: String = "",
    val kind: VideoCollectionKind = VideoCollectionKind.MULTI_PAGE,
    val tracks: List<Track> = emptyList(),
) { val size: Int get() = tracks.size; val isCollection: Boolean get() = tracks.size > 1 }

// core/contracts/Contracts.kt · MusicRepository
/** 普通单视频返回 Result.success(null)。 */
suspend fun getVideoCollection(bvid: String): Result<VideoCollection?> = Result.success(null)
```

- 调用方：新页面 `VideoCollectionScreen`、`SearchScreen`。
- 数据源（匿名只读）：`GET /x/web-interface/view`（主）；`GET /x/polymer/web-space/seasons_archives_list`（season 兜底分页）。
- 无本地持久化 schema 变更；无 API/文档需要同步（内部契约）。
- 需要成对修改：core 契约与 data 实现必须同批；UI 依赖该契约。

## 6. 风险与人工门禁

| 风险 | 影响面 | 缓解方式 | 需要人决定？ |
| --- | --- | --- | --- |
| 点击多一次 view 请求，弱网变慢 | 搜索点击体验 | 24h 缓存 + 行内 loading + 失败回退单曲播放（AC7） | 否（用户已选方案 A） |
| 100 首保存耗时/半截歌单 | 本地歌单一致性 | 保存中显示进度、禁用重复点击；`NonCancellable` 保护已开始的保存 | 否 |
| season 兜底接口未匿名开放/形状变化 | season 合集识别 | 主路径用 view；兜底失败时保留 view 内 episodes 并提示不完整 | 否 |
| 与登录态合集概念混淆 | 页面入口/文案 | 新页面文案用「合集」并标注来源为视频分集/合集；不改登录态合集页 | 否 |
| 真机网络/账号态差异 | 验收 | 真机断网 AC7/AC8；默认匿名状态验证 | 否 |

## 7. 待确认问题（阻塞项）

| # | 问题 | 需要谁回答 | 若无人回答，默认假设 |
| --- | --- | --- | --- |
| — | 无（3 个关键取舍已由用户拍板，见 recon §0） | — | — |

## 8. 变更记录

| 版本 | 日期 | 变更内容（old → new） | 确认人 |
| --- | --- | --- | --- |
| v1 | 2026-11-22 | 初稿；依据用户答复：交互=合集页、范围=仅搜索、保存=每次新建 | 用户（口头/选项确认） |
