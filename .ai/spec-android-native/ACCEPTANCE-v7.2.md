# 验收包 v7.2 — 横滑收敛到内容区 / 状态栏连贯 / 导出备份修复

**级别**：L2 · 分支 `feat/player-fixed-controls`（基于 `5088f44` v0.7.1）· 目标版本 versionCode 10 / 0.7.2
**状态**：已实现、真机验证通过、全量门禁绿；**已合入 main 并发布 v0.7.2**（CI #43789084301 success）

## 1. 用户需求
1. 播放页横滑只作用于「状态栏下方、歌曲名上方」的内容区（封面/歌词/评论）；歌名及以下控件固定；去掉播放控件下的「歌词」按钮。
2. 现代 Android edge-to-edge：状态栏与界面连贯（背景延伸到状态栏下方、无独立色带），状态栏图标可读。
3. 修复「导出备份」点按无反应（v0.5.0 起）。

## 2. 改动清单

| 文件 | 改动 |
|---|---|
| `PlayerScreen.kt` | `HorizontalPager` 只包住中间内容区（`weight(1f)`）；歌名/收藏、进度条、播放控制、底部操作行移到 pager 外固定；删除「歌词」按钮与 `MiniTransport`/`MainPlayerContent`；封面页用 `BoxWithConstraints` 自适应方形封面 |
| `AppRoot.kt` | `NavHost` 去掉全局 `statusBarsPadding()`；新增状态栏/导航栏图标颜色跟随当前界面（播放页亮图标、纯白播放页深图标、其它页面跟随主题）；MIUI `LocalView.context` 逐层解包取 Activity；新增 `findActivity()` |
| 15 个 screen 文件 | 根布局补 `statusBarsPadding()`（Home/Search/Library/Profile/Settings/Playlist/Downloads/Import/Leaderboard/History/Login/WatchLater/FavoriteFolder/CollectionDetail/Uploader） |
| `MainActivity.kt` | 显式透明系统栏（`SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)`）、关闭系统对比遮罩、`setDecorFitsSystemWindows(false)` |
| `themes.xml` | 状态栏/导航栏透明、`windowLayoutInDisplayCutoutMode=shortEdges`、`enforce*Contrast=false`、`windowLightNavigationBar=true` |
| `file_paths.xml` | 增加 `<cache-path name="backups" path="." />`（备份 zip 在 cache 根目录，FileProvider 才能分享） |
| `SettingsScreen.kt` | 导出备份成功/失败改用系统 Toast（原来提示在页面底部屏幕外） |
| `app/build.gradle.kts`、`README.md` | versionCode 10 / 0.7.2；README v7.2 说明 |
| `.ai/spec-android-native/SPEC-v7.2.md` | 本任务冻结 SPEC |

## 3. AC 与证据

| # | AC | 结果 | 证据 |
|---|---|---|---|
| 1 | 横滑只在中间内容区切换；在歌名行/控件行横滑不切页 | PASS | 真机 dump：在歌名行（y≈1195）与控件行（y≈1821）横滑后仍停留歌词页；中间区域横滑正常切页；截图 `evidences v8/12、13` |
| 2 | 底部操作行无「歌词」按钮 | PASS | dump descs = 顺序播放/定时关闭/倍速/队列/更多（无歌词）；截图 `v8/12` |
| 3 | 播放页背景延伸到状态栏（无浅色横条）；其它页内容不被状态栏遮挡 | PASS | 播放页截图 `v8/11、12`（状态栏区域为封面渐变）；首页截图 `v8/10`（内容在状态栏下方、背景同色） |
| 4 | 状态栏图标：亮色页深色、深色页亮色、纯白播放页深色 | PASS | `v8/12`（播放页亮图标）、`v8/16`（纯白播放页深图标）、`v8/17`（深色主题亮图标） |
| 5 | 导出备份弹系统分享面板 + Toast | PASS | 真机点按后 dump：`正在分享 1 个文件 / bbplayer-backup-20261116-0954.zip`；截图 `v8/20-export-share-sheet.png` |
| 6 | 全量门禁 | PASS | `assembleDebug + assembleRelease + app/data/playback/lyrics 单测` exit 0（615 tests / 0 fail） |

## 4. 发布记录（v0.7.2）

| 项 | 值 |
|---|---|
| 版本 | versionCode **10** / versionName **0.7.2** |
| Tag / main | `v0.7.2` → `b2290f9`；[CI #43789084301](https://github.com/small-ora/bbplayer-android/actions/runs/43789084301) success |
| Release | https://github.com/small-ora/bbplayer-android/releases/tag/v0.7.2 （资产仅 `app-release.apk`，4.23 MB，固定签名；`aapt2` 校验 versionCode 10 / 0.7.2；SHA-256 与 v0.7.1 同一证书，可直接覆盖升级） |
| 产物 | debug + release（固定签名，与 v0.7.1 同密钥） |

## 5. 风险 / 回滚
- 回滚：`git revert` 本提交，或回到 `v0.7.1`；无数据迁移、无偏好键变更。
- `file_paths.xml` 新增 cache 根路径只影响本 App 自己生成的 URI，不对外暴露。
- 状态栏图标颜色由 `WindowInsetsControllerCompat` 设置；极少数 ROM 若强制系统栏外观，可能以系统设置为准。

## 6. 证据索引
- `.ai/evidence/v8/10-home-light.png`、`11-player-statusbar.png`、`12-player-statusbar-icons.png`、`13-lyrics-fixed-controls.png`、`16-purewhite-statusbar.png`、`17-home-dark-statusbar.png`、`20-export-share-sheet.png`
