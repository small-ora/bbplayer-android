# GitHub Actions

`android.yml` 只做安卓端，一条流水线完成：

1. `data / playback / lyrics` 单元测试（613 用例）
2. `:app:assembleDebug`（可直接安装）
3. `:app:assembleRelease`（**固定 release 签名**：CI 从仓库 secrets 解码 keystore，签名后可通过 `apksigner verify` 校验；本地无签名材料时回退为未签名构建）
4. 上传两个 APK 与测试报告为 workflow artifacts
5. 推送 `v*` tag 时自动创建 GitHub Release 并附上**固定签名**的 `app-release.apk`（debug APK 只进 workflow artifacts，不发布，避免调试签名不一致）

触发方式：push 到 `main`、PR、手动 `workflow_dispatch`；打 tag 触发 Release。

### 签名 secrets（仓库 Settings → Secrets and variables → Actions）

| Secret | 含义 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | 固定 release keystore（`*.jks`）的 base64 |
| `RELEASE_STORE_PASSWORD` | keystore 口令 |
| `RELEASE_KEY_ALIAS` | key alias（默认 `bbplayer`） |
| `RELEASE_KEY_PASSWORD` | key 口令 |

本地构建想产出带签名的 release APK：在仓库根目录放 `keystore.properties`（已 gitignore）：
`storeFile=/绝对路径/release.jks`、`storePassword=…`、`keyAlias=bbplayer`、`keyPassword=…`。

本地等价命令：

```bash
./gradlew :data:testDebugUnitTest :playback:testDebugUnitTest :lyrics:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleRelease
```
