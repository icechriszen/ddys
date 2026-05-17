# Release

本项目通过 GitHub Actions 在推送版本 tag 时构建并发布 APK。Release workflow 位于 `.github/workflows/release.yml`。

## 版本号

发布前在 `app/build.gradle.kts` 中更新：

```kotlin
versionCode = 13
versionName = "1.2.9"
```

建议 tag 使用和 `versionName` 对齐的格式：

```text
v1.2.9
```

## GitHub Secrets

Release workflow 需要以下仓库 secrets：

- `SIGNING_KEY`：Android keystore 的 base64 内容。
- `KEY_ALIAS`：签名 alias。
- `KEY_STORE_PWD`：keystore 密码。
- `KEY_PWD`：key 密码。

不要把 keystore、密码或 base64 后的签名文件提交到仓库。

## 发布前检查

```bash
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
git diff --check
```

如果使用本地 runtime：

```bash
source scripts/env.sh
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
git diff --check
```

## 发布步骤

1. 更新 `versionCode`、`versionName` 和 `CHANGELOG.md`。
2. 合并到 `main`。
3. 创建并推送 tag：

```bash
git tag v1.2.9
git push origin v1.2.9
```

4. 等待 GitHub Actions 完成 release workflow。
5. 检查 Release 页面中是否生成：

```text
ddys-v1.2.9.apk
ddys-v1.2.9.apk.sha256
```

6. 在已安装旧版本的设备上打开设置页，确认应用内更新可以发现并下载安装新版本。

## 回滚

如果 release 存在严重问题：

- 在 GitHub Release 页面标记为 pre-release 或删除有问题的 APK 资产。
- 尽快发布修复版本，不要复用已经发布过的 tag。
