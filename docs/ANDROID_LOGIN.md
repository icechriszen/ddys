# Android 网站验证

Android 1.6.0 起支持上游 Cap 验证。

## 使用

- TV 模式打开“网站登录”，默认显示二维码。手机和电视连接同一局域网，用 Safari 或 Chrome 扫码，在网页中点击验证框，通过后点击“进入”。电视确认可读取目录后自动返回。
- 二维码有效期为 10 分钟。离开登录页会关闭连接，返回或点击“重新生成二维码”可生成新地址。
- 手机模式默认使用本机 WebView，也可选择手机扫码入口；未安装 WebView 的电视可直接扫码。
- 本机网页只有确认进入实际目录页面后才保存会话；旧验证码页面仍保留辅助输入兼容。

## 实现约束

`BrowserLoginBridge` 只转发源站表单、页面引用的同源资源和明确许可的 Cap 接口；手机执行上游原始脚本，验证结果仍由上游判定。桥接使用随机地址、Host/Origin 校验、请求大小限制、超时和短期配对 Cookie。

本地响应保留 `Referrer-Policy: same-origin`，否则 iPhone Safari 表单提交可能使用 `Origin: null` 并被拒绝。诊断脚本只显示真实 Cap 错误，不自动点击或伪造完成事件。

源站 Cookie 留在 Android 设备，连同验证浏览器的 User-Agent 持久化；请求按源站协议、域名、端口以及 Cookie 路径和有效期过滤。HTTP 和播放客户端在每次网络请求时添加 Cookie，防止源站重定向将会话带到媒体 CDN。

## 验证

```sh
./gradlew testDebugUnitTest :app:assembleDebug
```

`BrowserLoginBridgeTest` 使用本地 MockWebServer，覆盖原始脚本转发、表单失败不保存会话、成功后复查目录、Origin/Host 校验、配对 wasm 和生命周期；`SourceSessionPersistenceTest` 覆盖会话迁移、UA、Cookie 有效期、路径和源站隔离。

实机验收应检查扫码、真实验证回传、目录加载、播放和应用重启后的会话恢复。手机切换 TV 模式可检查流程，但不能替代真实电视遥控器和系统 WebView 的兼容性测试。

## 2026-11-14 验证记录

- Android 1.6.0（27）Debug APK 构建通过；105 项单元测试通过，无失败或跳过。
- APK SHA-256：`637221901c16c30b8b8fc1391bb610ee41f19331a36430f1de4e2d59c3b341fd`。
- 用户指定设备：`192.168.8.238:5555`，Samsung SM-S921B，Android 15。
- 独立测试包为 `com.jing.ddys.debug`，不覆盖设备上原有 `com.jing.ddys`。
- 用户确认本次安装后，已安装并启动 1.6.0-debug，切换 TV 模式并显示局域网二维码；实机伪造 Host 请求返回 403。
- 用户使用 iPhone Safari 扫码后确认验证通过，设备自动返回首页；实际观察到目录和封面加载成功。
- 通过方向键和确认键进入《野兽之心》，实际播放器出现影片画面，进度达到 0:07 / 1:44:59。
- 强制停止并重启测试包后仍保持登录；切换“本季新番”成功加载新内容，当前新进程源站首页、分页及分类请求均返回 HTTP 200，无需再次验证。
- 此次为 Samsung 手机的 TV 模式验收，尚未覆盖独立 Android TV 盒子的系统差异。
- 本机原目录增量缓存损坏，最终在 `/tmp/ddys-android-build` 中按当前源码构建；JDK 测试进程使用 `JAVA_TOOL_OPTIONS=-Djdk.lang.Process.launchMechanism=FORK` 启动。

## 1.6.1（28）回归

移除“一起看”后，101 项单元测试及 Debug APK 构建通过。原扫码会话在升级、手机／TV 模式切换及应用重启后继续有效；手机和 TV 模式均完成播放、字幕、选集、播放历史检查。本版没有清除会话重新触发真人验证。

同步 Worker 已在 Cloudflare 先通过 Durable Object 删除迁移清除房间状态，再删除 Worker；仓库不再包含同步服务源码或端点配置。未发现可确认专用于该功能的 API token，共享 Wrangler 登录及签名凭据保留。
