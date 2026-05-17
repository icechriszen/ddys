# Development

## 环境要求

- JDK 17。
- Android SDK，建议安装 Android SDK Platform 34、Build Tools 和 Platform Tools。
- Android Studio 可选，但推荐用于调试 UI 和模拟器。

仓库中的 `scripts/env.sh` 会指向本地 `runtimes/` 目录。该目录不进入 Git，适合维护者在本机固定 JDK、Android SDK 和 Gradle 缓存；普通贡献者可以直接使用系统环境和 Gradle Wrapper。

## 常用命令

运行单元测试：

```bash
./gradlew testDebugUnitTest
```

构建 debug APK：

```bash
./gradlew :app:assembleDebug
```

构建 release APK：

```bash
./gradlew :app:assembleRelease
```

使用本地 runtime 脚本：

```bash
source scripts/env.sh
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

使用项目脚本构建 debug APK：

```bash
./scripts/build-apk.sh
```

安装最新本地 APK：

```bash
./scripts/install-latest-apk.sh
```

## 架构导览

```text
app/src/main/java/com/jing/ddys/
├── compose/      # TV 与手机界面的 Compose 组件
├── detail/       # 详情页和详情数据状态
├── history/      # 播放历史
├── main/         # 应用入口和首页数据
├── playback/     # Media3/ExoPlayer 播放、字幕、剧集切换和进度
├── repository/   # HTTP、页面解析、分页和视频源授权
├── room/         # Room 数据库、实体和 DAO
├── search/       # 搜索、语音输入和搜索历史
├── setting/      # 设置页、代理、视频源登录和更新入口
└── update/       # GitHub Releases 更新检查、APK 下载、校验和安装
```

## 测试策略

- 解析、版本判断、文件名处理、校验和更新策略应优先写 JVM 单元测试。
- 播放、登录、代理和安装更新涉及设备能力，PR 描述中需要写明手工验证设备或模拟器。
- 改动跨越 `repository`、`room`、`playback` 或 `update` 时，请运行完整 `testDebugUnitTest` 和 `:app:assembleDebug`。

## 调试建议

- Android TV UI 优先用 TV 模拟器或真实电视盒子验证焦点移动。
- 播放问题请记录视频源类型、字幕状态、清晰度、失败时间点和 logcat。
- 更新问题请确认 GitHub Release 中存在 `ddys-<tag>.apk` 资产，并检查设备是否允许安装未知来源应用。

## 代码约定

- 保持功能改动聚焦，避免混入无关格式化。
- 新增共享逻辑时优先放到现有包边界内，不为单一调用点过早抽象。
- 用户可见文案放在 `app/src/main/res/values/strings.xml`。
- 不提交本地 runtime、IDE 缓存、签名文件、账号信息或构建产物。
