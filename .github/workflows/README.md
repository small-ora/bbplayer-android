# GitHub Actions

`android.yml` 只做安卓端，一条流水线完成：

1. `data / playback / lyrics` 单元测试（166 用例）
2. `:app:assembleDebug`（可直接安装）
3. `:app:assembleRelease`（未签名，需自备签名才能分发）
4. 上传两个 APK 与测试报告为 workflow artifacts
5. 推送 `v*` tag 时自动创建 GitHub Release 并附上 APK

触发方式：push 到 `main`、PR、手动 `workflow_dispatch`；打 tag 触发 Release。

本地等价命令：

```bash
./gradlew :data:testDebugUnitTest :playback:testDebugUnitTest :lyrics:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleRelease
```
