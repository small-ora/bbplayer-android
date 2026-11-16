# SPEC v7.2 — 播放页横滑收敛到内容区、状态栏连贯（edge-to-edge）、修复导出备份

状态：实现冻结。级别 **L2**。基线：`0.7.1 (5088f44)`。目标：versionCode 10 / 0.7.2。

## 1. 用户需求
1. 播放页横滑**只作用于**「状态栏下方、歌曲名上方」的内容区（封面/歌词/评论），歌曲名及以下控件固定不动；去掉播放控件行里的「歌词」按钮。
2. 现代 Android edge-to-edge：状态栏与应用界面连贯（背景延伸到状态栏下方，无独立色带），状态栏图标可读。
3. （承接上一轮）v0.5.0 起「导出备份」点按无反应。

## 2. 实现
### A. 播放页布局
- `HorizontalPager` 仅包住中间内容区（封面/歌词/评论），`weight(1f)`；歌名/上传者/收藏、进度条、播放控制、底部操作行都放到 pager 之外固定显示。
- 删除底部操作行「歌词」按钮（歌词只靠横滑进入）；`MiniTransport` 等不再需要的组件移除。
- 评论仍内嵌第 3 页、按需加载、状态保持。

### B. 状态栏 / edge-to-edge
- 根因：`NavHost` 全局 `statusBarsPadding()` → 播放页渐变背景画不到状态栏下，状态栏区域露出 App 背景色形成「色带」。
- 修复：`NavHost` 不再整体加状态栏内边距；**除播放页外**的页面在自己的根布局加 `statusBarsPadding()`；播放页背景全屏（延伸到状态栏/导航栏下方），内容仍避让。
- 状态栏/导航栏图标颜色跟随界面：普通页跟随 App 主题（亮底深图标、暗底亮图标）；播放页默认亮图标，纯白墨水屏播放页用深图标。
- `MainActivity`：显式透明系统栏、关闭系统对比遮罩、`WindowCompat.setDecorFitsSystemWindows(false)`；主题加 `windowLayoutInDisplayCutoutMode=shortEdges` 与 `enforce*Contrast=false`。
- MIUI 等主题包装下 `LocalView.context` 可能不是 Activity，逐层解包取 Activity 再设置图标。

### C. 导出备份修复
- 根因：备份 zip 生成在 `cacheDir` 根目录，但 `file_paths.xml` 只开放 `cache/shared/` 与 `files/downloads/`，`FileProvider.getUriForFile` 抛异常被静默吞掉；提示又渲染在页面底部屏幕外。
- 修复：`file_paths.xml` 增加 `<cache-path name="backups" path="." />`；导出结果/失败提示改为系统 Toast（跨页面可见）。

## 3. AC
| # | AC | 验证 |
|---|---|---|
| 1 | 横滑只在中间内容区切换；在歌名/进度/播放控件/底部操作行上横滑不切页 | 真机：滑动前后 dump 对比；在歌名行与控件行横滑后仍停留原页 |
| 2 | 底部操作行无「歌词」按钮；「查看评论」仍可跳评论页 | dump content-desc 无歌词；页面仍 5 个操作图标 |
| 3 | 播放页背景延伸到状态栏，无浅色横条；其他页内容不被状态栏遮挡 | 截图：播放页状态栏区域为封面渐变；首页/设置标题位置正常 |
| 4 | 状态栏图标：亮色页深色、深色页亮色、纯白播放页深色 | 三张截图 |
| 5 | 导出备份：点按后系统分享面板出现且 Toast 提示成功（不再无反应） | 真机点按 + dump/截图 |
| 6 | 全量门禁：assembleDebug + assembleRelease + 各模块单测 exit 0 | Gradle 日志 |

## 4. 非目标
不改数据层/偏好键；不动其它业务；不改变横滑三页的内容本身。
