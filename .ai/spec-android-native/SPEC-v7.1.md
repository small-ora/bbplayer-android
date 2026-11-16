# SPEC v7.1 — 固定发布签名（修复「与已安装应用签名不同」无法升级）

状态：实现冻结。级别 **L2**（构建/CI/分发链路；不涉业务代码）。
基线：`410dce2`（v0.7.0）。目标版本：versionCode 9 / versionName 0.7.1。

## 1. 现象（用户反馈）

从 v0.5.0 覆盖安装 v0.7.0 时，安装器报 `安装失败(-7) 与已安装应用签名不同`。

## 2. 根因（已核实）

- 仓库原先没有 signingConfig：`:app:assembleRelease` 产出**未签名** APK（无法直接安装）。
- GitHub Release 里的 `app-debug.apk` 由 CI 的**临时 debug keystore** 签名；GitHub Actions 每次运行都是全新 runner，
  `~/.android/debug.keystore` 每次重新生成 → **每次构建签名都不同**，跨版本 debug APK 必然无法覆盖安装。
- 旧版本（≤v0.7.0）的已安装签名无法恢复，换用新签名后**需要一次性换装**（旧调试包与新 release 包包名也不同）。

## 3. 修复方案

1. 生成固定 release keystore（4096-bit RSA，10000 天），私钥只存本机 + GitHub Actions secrets（不入库）：
   `RELEASE_KEYSTORE_BASE64` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`。
2. `app/build.gradle.kts` 新增 `signingConfigs.release`：优先读 CI 注入的环境变量；本地可放 `keystore.properties`（已 gitignore）；
   无签名材料时回退未签名，保证本地也能构建。
3. CI：构建前 `base64 -d` 解码 keystore → 用固定签名构建 `app-release.apk` → `apksigner verify` 校验；
   Release 资产改为**只提供签名后的 `app-release.apk`**（debug APK 仅作为 workflow artifact 供开发用）。
4. 版本 0.7.1 / versionCode 9；README 增加安装/升级与签名说明。

## 4. 可验证验收标准（AC）

| # | AC | 验证方式 |
|---|---|---|
| AC1 | release APK 用固定 keystore 签名，证书 SHA-256 = `fff168510791d8ab56b2c1308155ea5ceef2ad498b20bdb495bf4819e4f1d7f4` | `apksigner verify --print-certs`；CI 中同样执行校验 |
| AC2 | 签名 release APK 可安装，且重复安装（同签名）成功 → 后续版本可覆盖升级 | 真机 `adb install -r` 两次 Success；检查 `dumpsys package` versionName=0.7.1 |
| AC3 | Release 只提供签名版 `app-release.apk`；版本 0.7.1/9 | GitHub Release 资产列表 + `aapt2 dump badging` |
| AC4 | CI 全绿（测试 + 签名构建 + 签名校验），tag 触发 Release | GitHub Actions run 结论 + Release 页面 |
| AC5 | 旧用户迁移路径在 README/验收包写清（旧调试包一次性换装 + 备份恢复） | 文档 diff |

## 5. 非目标

- 不改 Android 业务代码；不做 Play 商店签名/发布；不改调试版包名。
- 旧调试包的签名无法回溯匹配（不做「降级签名伪装」）。

## 6. 风险

- keystore 丢失将无法再发布可覆盖升级的版本 → 必须备份（本机 `~/.bbplayer-signing/` + secrets 均在仓库外/受控环境）。
- 公开仓库的 secrets 安全依赖 GitHub；本地 `keystore.properties`/`*.jks` 已加入 `.gitignore`，禁止入库。
