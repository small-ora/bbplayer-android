# BBPlayer · 安卓原生版

[![Android CI](https://github.com/small-ora/bbplayer-android/actions/workflows/android.yml/badge.svg)](https://github.com/small-ora/bbplayer-android/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/small-ora/bbplayer-android?display_name=tag)](https://github.com/small-ora/bbplayer-android/releases)

用 **Kotlin + Jetpack Compose** 重写的 [BBPlayer](https://github.com/bbplayer-app/bbplayer)（B 站音频播放器）核心体验。
界面与组件风格参考 **[椒盐音乐 Salt Player](https://github.com/Moriafly/SaltPlayerSource)**，直接基于其开源的 SaltUI 组件库（`io.github.moriafly:salt-ui`）。

> 本项目为学习/研究性质的第三方客户端，与 Bilibili 官方无关；数据来自 B 站公开接口，仅供个人听歌使用，请遵守 B 站用户协议。

## 功能

### v2 新增（非登录功能补齐）

- **下载与离线**：歌曲下载（Range 断点续传、并发 1–3）、下载管理（进行中/已完成/失败、取消/删除/清空）、**无网络播放已下载曲目**、导出到 `Music/BBPlayer`（附同名 `.lrc`）
- **外部歌单导入**：粘贴网易云 / QQ 音乐歌单链接或 ID → 解析歌单 → 逐曲匹配 B 站视频（限速、进度、匹配度、时长差）→ 可手动替换 → 保存为本地歌单
- **评论**：一级评论分页 + 楼中楼回复（只读，无需登录）
- **发现**：搜索联想词、B 站热搜、B 站音乐区排行榜 / 我的收听排行、按日收听历史
- **播放器与外观**：播放器背景（封面渐变 / 纯色 / **纯白墨水屏**）、迷你播放器（底部停靠 / 悬浮胶囊）、**强调色预设**、保留动态取色与深浅色
- **歌词增强**：逐字渐变渲染（SPL / 增强 LRC / YRC / QRC 解析）、**逐曲偏移持久化**、现代/经典两种样式、翻译开关、**桌面歌词悬浮窗**（拖动/字号/颜色/锁定）、**通知栏歌词行**、**歌词分享卡片**
- **其他**：本地备份/恢复（zip，含设置）、GitHub Releases 更新检查、播放队列保存为歌单、歌单排序与站内过滤

### v1 核心功能（仍保留）

- **发现**：B 站音乐区热门榜单、最近播放、下拉/点击刷新
- **搜索**：关键词、BV 号、av 号、`b23.tv` 短链、完整视频链接；最近搜索记录与清空
- **播放**：B 站匿名音频流（DASH，默认最高可用档）；后台播放 / 媒体通知 / 锁屏与蓝牙控制；队列、播放模式（顺序/列表循环/单曲循环/随机）、倍速、拖动进度、定时关闭、断点续播（启动恢复队列与进度）
- **歌词**：LRC / 增强 LRC / SPL / YRC / QRC 解析、逐行高亮 + 逐字渐变 + 自动居中滚动、翻译合并、在线获取（QQ 音乐 → 网易云）、手动搜索候选、粘贴 LRC、逐曲偏移、字号/样式/翻译开关
- **音乐库**：本地歌单（新建/删除/添加/移除/播放全部）、本地收藏（红心）、最近播放
- **我的**：累计收听时长、播放次数、最近 12 周听歌热力图；设置（主题模式、动态取色、音质、自动播放、断点续播、歌词字号/偏移、清除歌词缓存、关于）
- **主题**：浅色 / 深色 / 跟随系统；Android 12+ **莫奈动态取色**；椒盐风格设计 token（背景 `#F3F3F3`/`#202020`、主色 `#0470E6`/`#0088FF`、半透明卡片、8/16/24dp 圆角）
- **播放器视觉**：封面取色模糊背景 + 白色文字、大圆角封面、2dp 进度线（时间在下方）、裸图标控制行、底部功能行；专辑封面圆角与列表行整行高亮为 Salt 风格

## 截图

| 首页 | 播放器 | 歌词 |
| --- | --- | --- |
| ![home](.ai/evidence/shots/13-restore.png) | ![player](.ai/evidence/shots/15-add-to-playlist.png) | ![lyrics](.ai/evidence/shots/17-lyrics-offset.png) |

| 音乐库 | 我的 | 设置 |
| --- | --- | --- |
| ![library](.ai/evidence/shots/14-library-playlist.png) | ![profile](.ai/evidence/shots/07-profile.png) | ![settings](.ai/evidence/shots/08-settings.png) |

## 构建 & 运行

环境：JDK 17、Android SDK（compileSdk 36 / build-tools 36）、Gradle 8.13（wrapper）。

```bash
# 单元测试（data / playback / lyrics 三个模块，共 311 个用例）
./gradlew :data:testDebugUnitTest :playback:testDebugUnitTest :lyrics:testDebugUnitTest

# Debug APK
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk，安装包名 com.bbplayer.android.debug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release（未签名，需自备签名配置）
./gradlew :app:assembleRelease
```

> 首次运行会请求通知权限（Android 13+），用于媒体播放通知；拒绝后仍可前台播放。

### GitHub Actions 自动构建

推送到 `main` 或手动触发 `Android CI` 工作流即可自动跑测试并产出 APK（workflow artifacts 可下载）；
打 `v*` tag（如 `v0.1.0`）会自动创建 GitHub Release 并附上 debug / release APK。详见 [.github/workflows/README.md](.github/workflows/README.md)。

## 架构

```
app/       UI（Compose + SaltUI）、导航、主题、播放器/歌词/首页/搜索/音乐库/我的/设置、依赖装配
core/      领域模型 + 仓储/播放器契约（纯 Kotlin）
data/      Bilibili 接口（搜索/view/pagelist/playurl/ranking/related）、本地 JSON 持久化、DataStore 设置
playback/  Media3 ExoPlayer + MediaSessionService + 通知 + 队列/模式/定时/断点续播 + 流地址惰性解析与 403 重试
lyrics/    LRC 解析、QQ/网易云歌词获取与匹配、缓存
```

- 单次 `./gradlew :app:assembleDebug` 可复现构建；CI 未配置。
- 安卓权限：`INTERNET`、`POST_NOTIFICATIONS`、`FOREGROUND_SERVICE(_MEDIA_PLAYBACK)`、`WAKE_LOCK`。**不请求、不保存任何账号凭据**。

## 已知限制（v1 非目标）

- 不做 B 站账号登录 / 收藏夹 / 合集 / UP 主 / 稍后再看（匿名播放 30280/30232/30216 三档）
- 逐字歌词依赖逐字时间轴：匿名接口目前只给到逐行 LRC，逐字效果在 SPL/增强 LRC 歌词文本下生效（解析器已支持 YRC/QRC）
- 未做第三方状态栏歌词模块（SuperLyric/Lyricon/魅族）、车载歌词、频谱、响度均衡、付费音质
- 不做 B 站装扮皮肤、桌面歌词、状态栏歌词、播客字幕、评论、歌单导入导出、下载与 m4a 导出
- 未实现 WBI 签名（当前匿名只读接口可用；若 B 站风控收紧需补）
- 导出音频为 B 站 m4s 容器（命名 .m4a），未嵌入元数据/封面
- 搜索/排行榜结果的 `cid` 可能为 0，播放时通过 pagelist 兜底取首 P（多 P 视频默认播 P1）
- 统计/热力图基于本机历史，不联网

## 开源许可与致谢

- 本项目代码：**MIT**（见 `LICENSE`）
- 功能与交互参考：[bbplayer-app/bbplayer](https://github.com/bbplayer-app/bbplayer)（MIT），未复制其 React Native 代码
- UI 组件库：[Moriafly/SaltUI](https://github.com/Moriafly/SaltUI)（**Apache-2.0**，Maven POM 声明；注：2.8.6 的部分源文件头仍残留 LGPL-2.1 声明，建议分发前与人确认一次）。归属展示在 App「设置 → 关于」中
- 设计参考：[Salt Player（椒盐音乐）](https://github.com/Moriafly/SaltPlayerSource)
- 数据来源：Bilibili 公开接口；歌词：QQ 音乐 / 网易云音乐公开接口。仅供学习研究，请勿用于商业用途
