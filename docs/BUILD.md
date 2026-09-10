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

## 个人试用版签名

维护者保存 `.local/signing/daybook-release.jks` 和根目录 `keystore.properties`，两者均被忽略。请将这两份材料另行保存在自己的安全备份中，丢失签名会影响以后直接覆盖升级。

`keystore.properties` 格式（示例值需替换）：

```properties
storeFile=.local/signing/daybook-release.jks
storePassword=YOUR_LOCAL_PASSWORD
keyAlias=daybook
keyPassword=YOUR_LOCAL_PASSWORD
```

配置后执行 `./gradlew :app:assembleRelease`，产物为 `app/build/outputs/apk/release/app-release.apk`。没有签名配置时只能生成未签名 release 包，不可当作安装包分发。不要使用不同机器临时生成的 debug 签名发布更新。

覆盖安装专项测试位于 `UpgradeContinuityTest`，常规测试会跳过此项。需先运行 `daybookUpgrade=seed`，安装同签名新版 APK，再运行 `daybookUpgrade=verify`；测试 APK 也必须使用相同签名。仅在专用模拟器执行。

## 测试数据

自动测试只使用虚构样例和隔离数据库；真实日历记录、导出的 JSON 和本地签名不得进入 Git。

## 第二阶段专项验收

常规仪器用例在专用 API 35 模拟器运行；提醒用例通过测试工具设置通知与准时提醒权限。

- 拒绝通知：先在系统撤销通知权限，再以 `daybookDenied=true` 运行 `ReminderTest#deniedPermissionKeepsReminderPending`。
- 进程退出/重启：以 `daybookLifecycle=seed` 运行 `ReminderLifecycleTest`，退出后台进程或重启；先从系统通知确认送达（此时不要重新打开 App），再以 `daybookLifecycle=verify` 检查持久化状态并清理样例。
- 跨版本：先安装真实旧 APK 与对应旧测试 APK 运行 seed；直接覆盖新版 APK，替换测试 APK 后运行新版 verify。不能仅用当前 debug 与当前 release 冒充历史升级验证。

Room 历史 schema 位于 app/schemas；新增结构需提供迁移及历史 schema 测试，禁止破坏性重建。旧 JSON 导入保留稳定 ID，导出统一采用格式 3。
