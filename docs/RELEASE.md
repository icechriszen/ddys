# Release

本项目通过 GitHub Actions 在推送版本 tag 时构建并发布 APK。Release workflow 位于 `.github/workflows/release.yml`。

## 版本号

发布前在 `app/build.gradle.kts` 中递增 `versionCode`，并设置新的 `versionName`。以已发布的 v1.5.1 为例：

```kotlin
versionCode = 26
versionName = "1.5.1"
```

建议 tag 使用和 `versionName` 对齐的格式：

```text
v1.5.1
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
version_name=$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts)
test -n "$version_name" && git tag "v$version_name" && git push origin "refs/tags/v$version_name"
```

4. 等待 GitHub Actions 完成 release workflow。
5. 检查 Release 页面中是否生成：

```text
ddys-<tag>.apk
ddys-<tag>.apk.sha256
update.json
```

6. 核对 `update.json` 中的 tag、APK 下载地址、文件大小和 SHA-256 摘要与发布资产一致。
7. 在已安装旧版本的设备上打开设置页，确认应用内更新可以发现并下载安装新版本。

## 回滚

如果 release 存在严重问题：

- 在 GitHub Release 页面标记为 pre-release 或删除有问题的 APK 资产。
- 尽快发布修复版本，不要复用已经发布过的 tag。
