审查者=fresh subagent · 审查对象=工作区未提交的 v5 变更（分支 main，基线 HEAD=53de350，`git status`/`git diff` 全部未提交改动）· 是否看过实现过程=否

审查方式：逐文件只读审查 diff 与新增文件；全仓库 grep（SESSDATA / bili_jct / DedeUserID / Cookie / Log / println / HttpLoggingInterceptor / 权限 / cleartext / WebView / Clipboard）；核实 OkHttp 4.12 重定向与 Header 校验的字节码行为；执行 `./gradlew :data:testDebugUnitTest --offline`（EXIT=0，39 个 suite / 345 个用例全绿，无 skip）。

## Findings

[P2] data/src/main/java/com/bbplayer/android/data/auth/BiliAuthRepository.kt:205-209 — `persist()` 丢弃 `store.save()` 返回值；`SecureCookieStore.kt:46-49` 的 `SharedPreferences.commit()` 返回值同样被忽略 — Keystore 不可用 / 磁盘写入失败时 `save()` 会返回 false（并已清空旧密文），但 `_session` 仍照常进入登录态：用户看到"登录成功"，凭据实际没有落盘，下次冷启动会静默掉登录，与"登录成功即已加密落盘"的红线承诺不一致 — 建议：`persist()` 检查 `save()` 结果，失败时撤销内存态并返回可重试错误；`SecureStorage.write` 改为返回 Boolean 并把 `commit()` 结果传出。

[P2] data/src/main/java/com/bbplayer/android/data/auth/BiliAuthRepository.kt:155-166 — `refreshUser()` 全仓库没有调用方（只有接口声明与 StubRepositories 的假实现），运行期接口返回 -101/-111 不会触发 `logout()` 清密文 — SESSDATA 在别处失效后，旧凭据会一直留在加密存储里直到下次冷启动 `restoreSession()` 才删除；期间界面仍显示已登录，收藏 / 合集 / 稍后再看全部报错。"退出即删除"满足，但"校验失败即删除"只在启动时成立 — 建议：`BiliApi` / 内容仓储把 -101/-111 回传给 `AuthRepository` 触发清理，或在回到前台时调用 `refreshUser()`。

[P2] data/src/main/java/com/bbplayer/android/data/BiliApi.kt:199-208（配合 data/.../cache/CachedRequestExecutor.kt:40-42） — `cacheable` 与 `identity` 在节流排队**之前**采样，真正发请求是 `throttle.acquire()`（350ms±100ms）之后才读 Cookie — 排队窗口内完成登录 / 启动恢复登录态时，会带着 SESSDATA 取回响应，却以 `[anon]` key 和正常 TTL 写入磁盘缓存（缓存 key / 写入侧身份与请求侧身份不一致），破坏类注释"登录态响应不落盘、登录/匿名缓存互不复用"的不变量；影响限于同机同端，但属于缓存隔离边界的 TOCTOU — 建议：在 `fetch` 内实际发请求前再判定身份/可缓存性，或在写缓存前重新比对 `authIdentity()`，不一致就不写。

[P2] data/src/main/java/com/bbplayer/android/data/BiliApi.kt:48-65,240,279（以及 data/.../auth/BiliAuthRepository.kt:240-242） — 登录 Cookie 是手动加到 `followRedirects(true)` 的共享 OkHttp client 上；已用 OkHttp 4.12 字节码核实 `RetryAndFollowUpInterceptor` 只在跨 host 重定向时移除 `Authorization`，**不会**移除手写的 `Cookie` 头 — 一旦 api.bilibili.com 返回指向非 bilibili 域的 3xx（服务端配置 / 未来开放重定向），SESSDATA / bili_jct 会跟随重定向外发到非 bilibili host，"凭据只发往 bilibili.com"只在"无重定向"前提下成立。TLS 使其难以被中间人触发，属于纵深防御缺口 — 建议：给带 Cookie 的请求加 interceptor，host 不属于 `*.bilibili.com` 时移除 Cookie（或对登录态请求关闭 redirect 跟随）。

[P2] data/src/test/java/com/bbplayer/android/data/auth/SecureCookieStoreTest.kt:28-36 — "落盘是密文"这条断言用的是 `FakeCookieCipher`（固定 12 字节前缀 + 反转明文），能验证存储层逻辑，但无法发现"没有真正使用 AndroidKeyStore""IV 固定/复用"这类实现回归；仓库没有任何 androidTest，也没有 BiliApi 级测试断言"登录 Cookie 只注入 B 站请求""登录态 GET 不写缓存"（`BiliApiErrorsTest` 只测错误文案） — 红线的两条核心不变量目前只有代码约定、没有可失败测试，`POST`/缓存路径改动不会被 CI 拦住 — 建议：补 instrumentation（或 Keystore 替身）测试验证 IV 随机与篡改失败；给 BiliApi 加 fake 拦截器测试，断言 Cookie 头存在性与缓存目录为空。

## 已核查、未发现问题的项

- 凭据存储：生产路径确实走 `AndroidKeyStore` + `AES/GCM/NoPadding`（256 bit、随机 IV 由系统生成并随密文保存 `iv||ciphertext`）；SharedPreferences 名为 `auth_secure`，值为 Base64(iv+密文)，无明文 / 无 JSON 明文；Base64 / GCM / 密钥丢失 / JSON 损坏全部安全清空并返回空；`logout()` 同时删密文与 Keystore key；`allowBackup=true` 备份到的只是无密钥不可解的密文。
- 日志与隐私：data 层无 `Log`/`println`/日志拦截器；异常链只含 URL（不含 Header）；OkHttp 4.12 对 `Cookie`/`Set-Cookie` 的 Header 校验异常已脱敏（字节码核实不再拼接 value）。
- 网络外发：带凭据的请求只落到 `api.bilibili.com` 与 `passport.bilibili.com`（常量拼接，非用户可控）；封面 CDN、音频 CDN、字幕 CDN、QQ/网易云、GitHub 均使用不带 Cookie 的独立 client；UA / Referer / Origin 合规；`usesCleartextTraffic=false`。
- 缓存隔离：登录态 GET 传 `ttlMs=null`（不读不写），缓存 key 带 `[u:<mid>|h:<12hex>|anon]` 身份；磁盘缓存文件名是 `sha256(key)`，mid / Cookie 短 hash 不落盘；退出登录的正常路径不会读到上一账号的接口缓存；WatchLater / Player 在 `loggedIn` 变化时清空内存列表，Library 未登录时只渲染登录引导、不渲染旧收藏夹 / 合集列表。
- CSRF / 越权：收藏 deal、稍后再看 add/del/clear、关注 modify、历史上报均显式带 `bili_jct` 且拒绝空值，`postForm(withCsrf=true)` 也会拒绝空 csrf；手动 Cookie 只保留 5 个白名单字段；扫码 qrcode_key 只来自本应用 `generateQrCode()`。
- UI 泄漏：登录页只有"用户自己粘贴的输入框"，界面不展示已存储 Cookie；错误文案不含凭据；无 WebView、无外部 Intent / 剪贴板读取。
- WBI：置换表、去 `!'()*`、RFC3986 编码、md5 与文档一致，单测使用独立 Python 向量；key 仅内存（字段缓存，不落盘）。
- 依赖 / 权限：无新增权限；zxing 仅用于二维码编码（LoginScreen:363-373）。

P0/P1 = 0；以上 5 条均为 P2（加固建议，不阻断验收），无 P0 / P1。

未发现影响安全性的问题
