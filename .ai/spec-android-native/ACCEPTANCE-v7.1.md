# 验收包 v7.1 — 固定发布签名（修复「与已安装应用签名不同」）

**级别**：L2（构建/CI/分发链路）· 直接合入 `main`（基于 `410dce2` v0.7.0 的补丁）
**状态**：**已实现并本地验证；待合入 main + 发布 v0.7.1（CI/Release 验证结果见 §4）**

## 1. 现象与根因

- 现象：v0.5.0 覆盖安装 v0.7.0 报 `安装失败(-7) 与已安装应用签名不同`。
- 根因：仓库原先无 signingConfig（release 未签名）；GitHub Release 的 `app-debug.apk` 用的是 CI runner 的**临时 debug keystore**，每次 Actions 运行都会重新生成 → 每次构建签名不同，跨版本必失败。旧版本签名无法恢复。

## 2. 修复内容

| 项 | 内容 |
|---|---|
| 固定 keystore | 4096-bit RSA，validity 10000 天；私钥仅存本机 `~/.bbplayer-signing/` + GitHub Actions secrets（不入库） |
| Gradle | `app/build.gradle.kts` 新增 `signingConfigs.release`：CI 读环境变量、本地读 `keystore.properties`（gitignore），无材料回退未签名 |
| CI | 解码 `RELEASE_KEYSTORE_BASE64` → 固定签名构建 → `apksigner verify --print-certs` 校验；Release 资产只放签名 `app-release.apk`（debug APK 仅 workflow artifact） |
| 版本 | versionCode 9 / versionName 0.7.1 |
| 文档 | README v7.1 安装/迁移说明；`.github/workflows/README.md` 增加签名 secrets 说明；`.gitignore` 排除 `*.jks`/`keystore.properties` |

## 3. 本地验证证据

- 签名构建：`RELEASE_STORE_FILE=… ./gradlew :app:assembleRelease` → exit 0，产物 `app-release.apk`（4.4 MB）
- 版本：`aapt2 dump badging` → `versionCode='9' versionName='0.7.1'`，包名 `com.bbplayer.android`
- 签名：`apksigner verify --print-certs` → `Signer #1 certificate DN: CN=BBPlayer, O=BBPlayer, C=CN`，
  **SHA-256 `fff168510791d8ab56b2c1308155ea5ceef2ad498b20bdb495bf4819e4f1d7f4`**
- 真机（MI 6）：签名 release APK 安装 Success；**重复安装同 APK Success**（同签名 → 后续版本可覆盖升级）；
  `dumpsys package com.bbplayer.android` → versionName=0.7.1；与旧调试包 `com.bbplayer.android.debug` 可共存
- 业务回归：release 包首次启动正常（通知权限弹窗 → 首页）

## 4. CI / Release 验证（发布后回填）

- 待回填。

## 5. 用户迁移步骤（旧版 → v0.7.1）

1. 打开旧版（`com.bbplayer.android.debug`）→ 设置 → 数据 → **导出备份**（保存 zip）。
2. 安装 v0.7.1 的 **`app-release.apk`**（签名固定；与旧调试包包名不同，可共存）。
3. 新应用 → 设置 → 数据 → **从备份恢复** → 重启应用。
4. 确认数据无误后可卸载旧版调试包。

## 6. 风险与回滚

- **必须备份 keystore**（`~/.bbplayer-signing/bbplayer-release.jks` + `credentials.txt`）；丢失将无法再发布可覆盖升级的版本。
- 本地 `keystore.properties` / `*.jks` 已 gitignore，禁止入库；私钥只存在于本机与 GitHub secrets。
- 回滚：revert 本签名提交（回到未签名 release），但已发布签名版本不受影响。
