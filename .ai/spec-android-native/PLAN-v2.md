# PLAN v2 — 非登录功能补齐

## 写范围（文件级，互不重叠）
| 角色 | 写范围 |
|---|---|
| Lead | core/**、app/src/main/java/.../{di,ui/theme,ui/components,ui/AppRoot.kt,ui/screens/{player,lyrics,settings,home,library,playlist,search}}、res/**、构建/CI/文档 |
| ui-v2 | **新增** app/src/main/java/.../ui/screens/{discover,history,comments,import,downloads,update}/** 与 ui/viewmodel/v2/**（不修改既有文件；入口由 Lead 接线） |
| data-v2 | data/**（新增 Discover/Comments/ExternalPlaylist/Subtitle/Update/Backup 实现 + 测试；可微调 DataStorePreferences/JsonHistoryRepository 中 v2 相关） |
| playback-v2 | playback/**（DownloadManager/离线播放/导出/淡出定时/通知歌词行 + 测试） |
| lyrics-v2 | lyrics/**（SPL 解析、逐曲 offset 存储、QRC/YRC 逐字 + 测试） |

## 步骤
1. 契约与骨架（Lead）：V2 契约、AppGraph 扩展、设置字段、stub 接线；构建绿。✅
2. 并行实现：data-v2 / playback-v2 / lyrics-v2 / ui-v2（各自模块单测绿）。
3. 集成（Lead）：RealAppGraph 真实现、路由与入口、播放器/歌词/设置改造、桌面歌词、分享卡片、平板 Rail、强调色/纯白。
4. 自检：FULL 门禁 + 真机走查 + 截图证据。
5. 审查：fresh reviewer 审查 v2 diff（重点：下载离线、导入限速、桌面歌词权限、隐私/无登录）。
6. 发布：合入 main、tag v0.2.0、CI 构建 Release 并真机验证 CI 产物。

## 验证命令
- QUICK：`./gradlew :app:compileDebugKotlin`
- FULL：`./gradlew :app:assembleDebug :app:assembleRelease :data:testDebugUnitTest :playback:testDebugUnitTest :lyrics:testDebugUnitTest`
- DEVICE：`.ai/scripts/ui.py` + `adb`（安装/截图/断网/桌面歌词/平板模拟：`adb shell wm size 1600x2560`）

## 重分级触发
需要登录态、需要改 v1 契约语义、引入新第三方 SDK/依赖、需要 B 站装扮商城接口 → 停下问人。
