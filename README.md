# 低端影视 Android TV 客户端

[![CI](https://github.com/icechriszen/ddys/actions/workflows/ci.yml/badge.svg)](https://github.com/icechriszen/ddys/actions/workflows/ci.yml)
[![Release](https://github.com/icechriszen/ddys/actions/workflows/release.yml/badge.svg)](https://github.com/icechriszen/ddys/actions/workflows/release.yml)
[![GitHub release](https://img.shields.io/github/v/release/icechriszen/ddys?include_prereleases&sort=semver)](https://github.com/icechriszen/ddys/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

低端影视 Android TV 客户端是一个面向电视和大屏设备的开源 Android 应用，提供影视浏览、搜索、播放历史、视频源登录、网络代理和应用内更新等能力。

> 本项目只提供客户端实现，不托管、不上传、不分发任何影视内容。内容、账号和视频源均来自用户访问的第三方站点，请自行确认使用行为符合当地法律法规及相关站点协议。

![首页截图](img/1671288587697.png)

## 功能

- Android TV / Leanback Launcher 支持，同时兼容触屏 Android 设备。
- 首页分页浏览、详情页、搜索和搜索历史。
- Media3 / ExoPlayer 播放，支持 HLS、字幕下载、播放进度恢复和剧集切换。
- 本地播放历史和搜索历史存储。
- 视频源登录状态管理。
- HTTP 代理配置，适合需要自定义网络出口的环境。
- 基于 GitHub Releases 的应用内更新检查、APK 下载和安装。

## 安装

### 从 Release 安装

1. 打开 [Releases](https://github.com/icechriszen/ddys/releases)。
2. 下载最新版本中的 `ddys-<version>.apk`。
3. 将 APK 安装到 Android TV、电视盒子或 Android 设备上。

如果当前还没有可用 Release，请使用下面的源码构建方式生成 debug APK。

### 从源码构建

```bash
git clone https://github.com/icechriszen/ddys.git
cd ddys
./gradlew :app:assembleDebug
```

构建产物位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

本地如果已经准备好仓库内的 Android/JDK runtime，可使用项目脚本：

```bash
source scripts/env.sh
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

更多开发环境、测试和发布说明见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) 与 [docs/RELEASE.md](docs/RELEASE.md)。

## 技术栈

- Kotlin + Android Gradle Plugin
- Jetpack Compose / Compose for TV / Leanback
- AndroidX Media3 / ExoPlayer
- Room / Paging
- Koin
- OkHttp / Jsoup / Gson
- GitHub Actions + GitHub Releases

## 项目结构

```text
app/src/main/java/com/jing/ddys/
├── compose/      # TV 与手机界面的 Compose 组件
├── detail/       # 详情页
├── history/      # 播放历史
├── main/         # 首页入口
├── playback/     # 播放器和剧集播放
├── repository/   # 网络请求、解析和数据模型
├── room/         # 本地数据库
├── search/       # 搜索
├── setting/      # 设置、代理、视频源登录
└── update/       # GitHub Release 更新检查和 APK 安装
```

## 参与贡献

欢迎提交 bug fix、文档改进、兼容性修复和清晰的功能增强。开始前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 和 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

提交 PR 前请至少运行：

```bash
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

## 支持与安全

- 使用问题和功能建议：请通过 [GitHub Issues](https://github.com/icechriszen/ddys/issues) 反馈。
- 安全问题：请阅读 [SECURITY.md](SECURITY.md)，不要在公开 issue 中披露可复现的利用细节。

## 许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 发布。
