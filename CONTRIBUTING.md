# Contributing

感谢你愿意改进低端影视 Android TV 客户端。这个项目希望保持小而清晰：修复应可验证，功能应和电视端观影体验直接相关。

## 开始前

- 先搜索现有 Issues 和 Pull Requests，避免重复工作。
- Bug 报告请尽量提供设备型号、Android 版本、应用版本、网络环境、复现步骤和截图或日志。
- 不要提交账号 Cookie、私有视频源、签名证书、构建密钥或任何无法公开的资源。
- 不要在 issue 或 PR 中粘贴侵权资源链接、绕过限制的步骤或第三方站点的敏感数据。

## 本地开发

```bash
git clone https://github.com/icechriszen/ddys.git
cd ddys
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

如果你的本地 checkout 包含 `runtimes/` 目录，可以使用：

```bash
source scripts/env.sh
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

更多说明见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

## Pull Request 要求

- 保持改动聚焦，一个 PR 只解决一个明确问题。
- 为行为变化补充或更新测试；无法自动化测试时，请在 PR 描述中写明手工验证步骤。
- UI 改动请附截图或录屏，并说明测试过的设备或模拟器尺寸。
- 更新用户可见行为时，同步更新 README、docs 或 changelog。
- 提交前运行：

```bash
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

## 代码风格

- Kotlin 代码优先沿用现有包结构和命名风格。
- 避免无关重构、格式化整文件或批量移动文件。
- 网络、解析、更新、播放等容易影响用户体验的改动应尽量保持小步提交并带测试。

## Release

维护者发布新版本时请参考 [docs/RELEASE.md](docs/RELEASE.md)。普通贡献者不需要处理签名密钥或 release secrets。
