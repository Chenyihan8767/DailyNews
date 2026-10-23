# 上传 GitHub 与构建 APK

## 哪些文件是源码

本项目是原生 Android 应用，使用 Kotlin、Jetpack Compose 和 Gradle。

| 路径 | 用途 |
| --- | --- |
| `app/src/main/java/com/dailynews/reader/MainActivity.kt` | Activity 入口 |
| `app/src/main/java/com/dailynews/reader/NewsApplication.kt` | 应用初始化 |
| `app/src/main/java/com/dailynews/reader/ui/` | 页面、交互、ViewModel 和主题 |
| `app/src/main/java/com/dailynews/reader/data/` | 数据库、订阅源、正文提取、AI 接口和设置 |
| `app/src/main/java/com/dailynews/reader/work/` | 后台任务和处理队列 |
| `app/src/main/res/` | 图标、样式等 Android 资源 |
| `app/src/main/AndroidManifest.xml` | 应用组件与权限声明 |
| `app/src/test/` | 本地单元测试 |
| `app/src/androidTest/` | 设备测试 |
| `app/build.gradle.kts` | App 依赖、SDK、版本与构建配置 |
| `build.gradle.kts`、`settings.gradle.kts`、`gradle.properties` | 项目构建配置 |
| `gradlew`、`gradlew.bat`、`gradle/wrapper/` | Gradle Wrapper，必须一起上传 |

应上传项目根目录下全部受 Git 跟踪的文件，而不是只上传 `.kt` 文件。`README.md`、`docs/`、`scripts/` 也可以一起上传。

`.tooling/` 是本机的 JDK、SDK 和 Gradle；`.gradle/`、`.kotlin/`、`build/`、`app/build/` 是缓存或构建结果；`local.properties` 包含本机 SDK 路径。这些都不应提交。密钥、签名文件、API Key 和本地凭据也不要提交，相关路径已在 `.gitignore` 中忽略。

## 上传到 GitHub

本地 Git 仓库和 GitHub 远程仓库是两回事：本地初始化不会自动上传。

1. 登录 GitHub，点击右上角 **+ → New repository**。
2. 仓库名可填 `DailyNews`，按需要选择 Public 或 Private。Public 会公开代码和提交历史。
3. 创建空仓库：不要勾选添加 README、`.gitignore` 或 License，以免与本地首次提交冲突。许可证可之后按实际授权需求添加。
4. 在本项目根目录执行以下命令，将 `YOUR_USERNAME` 替换为你的 GitHub 用户名：

```sh
git remote add origin https://github.com/YOUR_USERNAME/DailyNews.git
git push -u origin main
```

HTTPS 推送需使用 GitHub 支持的凭据管理器、登录工具或个人访问令牌，不能使用账号密码。不要将令牌写入远程 URL 或源码。已配置 GitHub SSH 密钥时，也可使用 `git@github.com:YOUR_USERNAME/DailyNews.git`。

若 `origin` 已存在，先用 `git remote -v` 检查；确实需要修改时再执行 `git remote set-url origin <新地址>`。

后续更新：

```sh
git status
git add .
git diff --cached
git commit -m "Describe your change"
git push
```

提交前检查暂存内容，不要上传 API Key、签名私钥或本机配置。APK 可通过 GitHub 仓库的 **Releases → Draft a new release** 附件分发，无需放进源码提交。

## 从源码构建可安装的 Debug APK

### Android Studio（推荐）

1. 安装支持 Android Gradle Plugin 8.9.2 的 Android Studio（Meerkat 2024.3.1 或更新兼容版本）。
2. 打开或克隆整个项目根目录，等待 Gradle 同步。
3. 在 SDK Manager 中安装 Android SDK Platform 35 和 Build Tools 35.0.0；Gradle JDK 使用 JDK 17 或 21。
4. 选择 `app` 模块和 `debug` 变体，使用 Build 菜单中的 Build APK(s) 功能；不同版本菜单名称可能略有不同。

### 命令行

安装 JDK 17 或 21 和上述 Android SDK，设置 `JAVA_HOME`，并通过 Android Studio 生成 `local.properties`，或自行写入 `sdk.dir=/你的/Android/SDK/路径`。SDK 路径因电脑而异，不要复制本项目工作区的路径。

macOS / Linux：

```sh
./gradlew :app:assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

首次构建需要联网下载 Gradle 和依赖，并可能需要接受 Android SDK 许可证。

在当前工作区，可以直接复用被忽略的 `.tooling/` 环境：

```sh
./scripts/build.sh
```

输出文件：`app/build/outputs/apk/debug/app-debug.apk`。该 APK 自动使用本机 Debug 密钥签名，可安装到 Android 8.0（API 26）及以上设备。手机安装时需允许对应来源安装应用；也可在连接已开启 USB 调试的设备后执行：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

如果安装提示签名不一致，需要使用与已安装版本相同的签名密钥，或者卸载旧版本后重装；卸载会删除应用本地数据。

验证源码：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

当前工作区也可使用 `./scripts/build.sh :app:testDebugUnitTest :app:lintDebug`。

## 构建用于分发的 Release APK

当前 `app/build.gradle.kts` 没有配置 Release 签名。直接运行 `./gradlew :app:assembleRelease` 得到的是未签名 APK，不能直接作为普通安装包分发。

在 Android Studio 中使用 **Build → Generate Signed App Bundle or APK → APK**，选择 `app` 模块，创建或选择自己的 keystore，选择 `release` 并完成构建。以完成提示中的路径定位签名 APK。

妥善备份 keystore、别名和密码，后续更新必须沿用同一签名。密钥存放在仓库外，密码不要写入源码。准备发布新版本时，在 `app/build.gradle.kts` 中递增 `versionCode`，并按需要调整 `versionName`。若要上架 Google Play，通常应生成签名 AAB，并满足届时商店的发布要求。
