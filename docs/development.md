# 本地开发

仓库根目录即源码与 Git 仓库所在目录。本机 Android SDK 路径写在未跟踪的 `local.properties`（`sdk.dir`）中，仓库不记录任何机器绝对路径。

## 一键验收

在项目根目录使用 PowerShell：

```powershell
.\dev.ps1 check
```

默认离线执行全部 `scripts/test-*.cjs` 桥接回归、`testDebugUnitTest`、`assembleRelease` 和 `lintVitalRelease`，随后核对 APK 包名、版本与原版签名证书，并生成 SHA-256 文件。

仅运行回归：`./dev.ps1 test`。仅构建并校验正式 APK：`./dev.ps1 build`。需要 Gradle 在线解析依赖时追加 `-Online`；已备份的 Robolectric 运行依赖仍从本地读取。

APK 输出位置：`app/build/outputs/apk/release/ComfyUIMobile-v<版本>-release.apk`。

## 交互开发

```powershell
. .\scripts\Enter-DevEnvironment.ps1
git status
gh auth status
.\gradlew.bat --offline --no-daemon assembleRelease
```

环境脚本只修改当前 PowerShell 进程，不修改系统环境变量。开发目录应使用纯英文路径。复制到另一位置后，需要同步更新未跟踪的 `local.properties` 中 `sdk.dir` 和私密签名配置中的 `storeFile`；不改变密码或私钥。

## 本地目录

| 路径 | 用途 |
| --- | --- |
| `JDK 21`（PATH） | 构建用 JDK，AGP 9 要求 JDK 21 |
| `ANDROID_HOME` 指向的 SDK | Android SDK 37、build-tools、ADB |
| `.local/android/signing` | 原正式签名密钥及私密配置 |
| `.local/tools` | 独立 Git、GitHub CLI、Node.js |
| `.local/cache` | Gradle、Maven 和 Robolectric 离线依赖 |
| `.local/recovery` | 重装前技术资料、截图、测试记录、原 APK、校验清单及旧交接资料 |
| `.local/logs` | 本次恢复和迭代验证日志 |

整个 `.local/` 均被 Git 忽略，不能整体上传到 GitHub。签名配置优先读取环境变量 `COMFY_SIGNING_PROPERTIES` 指定的文件，其次读取 `.local/android/signing/ComfyUIMobile-signing.properties`，最后兼容旧项目相邻的 `../app/tools/signing/ComfyUIMobile-signing.properties`。

GitHub CLI 的新系统登录凭据由 `gh auth login` 管理，不保存在项目或历史备份内。公开发布和实际手机安装应与本地构建验收分别记录。
