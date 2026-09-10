# 构建与安装

## 工具链

- Android SDK Platform 36、Build Tools 35.0.0、Platform Tools。
- JDK 21；编译目标 Java 17。
- Gradle 8.13 / Android Gradle Plugin 8.13.2 / Kotlin 2.2.21。
- Compose BOM 2025.12.01 / Room 2.8.4 / KSP 2.2.21-2.0.4。

使用 Android Studio 打开项目，选择兼容 JDK 21，安装上述 SDK。`local.properties` 中设置本机 `sdk.dir`，此文件不提交。

命令行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

第二条命令需要正在运行的安卓模拟器。仪器测试应在专用模拟器执行，不在日常使用的手机上运行。

## 安装

普通构建产物在 `app/build/outputs/apk/debug/app-debug.apk`。开发时可用 `adb install -r` 覆盖安装；正式个人试用版本从仓库 Releases 下载。

覆盖安装必须保持同一 applicationId 和签名。不要卸载旧应用再升级，否则本地数据会被清除。签名材料由维护者本地保存，不提交仓库。

## 测试数据

自动测试只使用虚构样例和隔离数据库；真实日历记录、导出的 JSON 和本地签名不得进入 Git。
