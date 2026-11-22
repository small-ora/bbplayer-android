# Recon — BBPlayer 原生 Android 重写（Salt Player 风格）

日期: 2026-11-15 · 执行者: Lead

## 1. 上游理解
- 上游仓库: https://github.com/bbplayer-app/bbplayer（MIT），本地克隆 `/tmp/bbplayer-upstream`，HEAD 见 `git -C /tmp/bbplayer-upstream rev-parse HEAD`。
- 深度功能/行为 recon: `.ai/recon/upstream-features.md`（1280 行，逐条 file:line 引用）。
- Salt Player 设计 recon: `.ai/recon/saltplayer-design.md`（507 行）+ 17 张截图 `.ai/recon/saltplayer/`。
- 关键结论：上游 = Bilibili 音频播放器（RN/Expo），核心是搜索→解析音频流→Media3 播放；歌词 SPL/逐字；MD3+莫奈；本地歌单/收藏/合集/下载/统计。v1 按用户拍板做「核心可玩版 + 匿名播放，不做登录」。
- Salt Player 设计系统开源: SaltUI（Compose 组件库）。本机 Gemfile 采用 `io.github.moriafly:salt-ui:2.8.6`（LGPL-2.1，源码可从 Maven 获取，需在 NOTICE 注明；3.0.0 为 Apache-2.0 但要求 compileSdk 37/AGP 9.1，本机 SDK 仅有 36，故使用 2.8.6）。

## 2. 工具链事实（实测）
- OS: Linux x86_64, 8 vCPU / 7G RAM, 磁盘可用 ~23G；网络到 dl.google.com / repo.maven.apache.org 可用（~12MB/s）。
- JDK: OpenJDK 17.0.21；Gradle 8.13（wrapper，本地已缓存发行版）；AGP 8.13.2；Kotlin 2.4.21；compose 1.10.0；compileSdk/targetSdk 36；minSdk 26。
- Android SDK: /opt/android-sdk（platforms/android-36, build-tools 35/36, licenses 已接受）。
- 设备: `adb devices` → `<device>  device`，型号 24129PN74C，Android 16（sdk 37），1200x2670 @520dpi，**可安装/截图/驱动 UI**。
- Bilibili 匿名 API 已实测可通（需 UA + Referer，search 需 buvid3 cookie）: `/x/frontend/finger/spi`、`/x/web-interface/search/type`、`/x/web-interface/view`、`/x/player/playurl`（DASH audio 30216/30232/30280）、`/x/web-interface/ranking/v2?rid=3`、`/x/web-interface/archive/related`。
- 第三方歌词 API 已实测: QQ `c.y.qq.com/soso/fcgi-bin/client_search_cp` + Netease `music.163.com/api/search/get/web` 可匿名返回。

## 3. 验证命令
- VALIDATION_FULL: `./gradlew :app:assembleDebug :app:testDebugUnitTest`（期望 exit 0）
- VALIDATION_QUICK: `./gradlew :app:compileDebugKotlin`（期望 exit 0）
- VALIDATION_SINGLE: `./gradlew :app:testDebugUnitTest --tests "com.bbplayer.android.*"`
- DEVICE_SMOKE: `adb install -r app/build/outputs/apk/debug/app-debug.apk` → `adb shell am start -n com.bbplayer.android.debug/com.bbplayer.android.MainActivity` → `adb exec-out screencap -p > shot.png` → `adb shell uiautomator dump`。
- CI: 无。ENFORCEMENT: prompt-only（无 pre-commit/CI required check）。

## 4. VCS / 提交
- 本目录为新建 git 仓库（无远端），分支 `main`，一主题一 commit。
- BASE: 见 `git log --oneline` 首个基线提交；SPEC_BASE: 见 `.ai/spec-android-native/SPEC_BASE`。
- DIFF_CMD: `git diff <SPEC_BASE>..HEAD`；LOG_CMD: `git log --oneline <SPEC_BASE>..HEAD`；CHECKPOINT: 本地 commit。

## 5. 红线与约束
- 不做账号登录/Cookie/扫码；不读取/存储任何用户凭据；不上传任何数据；第三方 API 只读。
- 不引入 B站 装扮皮肤引擎、桌面歌词悬浮窗、状态栏歌词（v1 非目标）。
- 依赖许可：SaltUI 2.8.6 为 LGPL-2.1（动态依赖，源码可得 + 本仓库提供全部应用源码，可重链接）；BBPlayer 上游 MIT 仅参考思路、不复制其 RN 代码。
- 生成物 `.ai/evidence/` 不入库；APK 不入库。

## 6. 已知风险
1. DASH `.m4s` 在 ExoPlayer 的播放兼容性需真机验证；备选 `durl`（音视频合一 mp4）。
2. 音频 CDN 需要 `Referer: https://www.bilibili.com/` + 桌面 UA，且 URL 有时效；需 403 时重新解析。
3. SaltUI 2.8.6 API 需以源码为准（源码已解压 /tmp/saltui-src/extracted 供参考）。
4. 歌词第三方接口无 SLA，需手动粘贴 LRC 兜底。
