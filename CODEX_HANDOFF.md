# Daybook 项目交接

更新：2026-09-13。当前已完成用户批准的 v0.4.0 实现、专项验收和本地交付；GitHub Release 公开发布等待用户明确授权。恢复时只核对实际 Git、CI 与已有输出，不重复实现或完整 QA，不自动开始 backlog。

## 当前成果

- v0.4.0 / versionCode 4，原签名保留。A/B `8ec8e4c`：单行年月、跟手分页和短滑回弹、具体时间未定、筛选重置。C `cfaec74`：独立便签。D `fd36ad0`：自选背景和本地配色。三个阶段都已推送 main，CI 均成功；业务交付代码为 8e03e3b，其 CI 34762134277 成功；随后仅补充交付状态文档。v0.4.0 标签尚未创建。
- 底部日历/任务/便签/回顾四入口。便签无强制标题，首行摘要、默认无日期、可选日期和独立提醒、自动保存/返回确保保存、编辑删除、按修改时间排序、通知直达。便签独立，不是第四种 EntryKind，不混入原日历/任务/回顾；空的新便签不保留，已有便签清空正文需要明确删除。
- Room 4 新增 memos 表；JSON 4 保存记录与便签，读 JSON 1–4。整体恢复旧备份会清空便签，确认页明确提示；替换前快照包含两张表，共用写锁和事务，失败回滚。编辑便签不会覆盖并发发送提醒状态。
- 外观设置：系统选图/低版本自动回退、本机图片副本、自动提取配色、预览再应用/恢复默认；四主页共享背景，编辑/设置保留清晰底色。普通强调色随主题，日历语义标记与错误色独立。图片和配色不进 JSON，首版不手动调色。没有新增第三方库或联网/相册范围权限。
- 包含此前 c13a6de 首次通知授权与一次可跳过引导、多品牌自启动/后台和悬浮建议。保留旧通知渠道与补发规则，不猜测厂商权限状态，不统一要求省电“无限制”。

## 待用户确认的公开发布

- 自动审批拒绝了 `git tag/push v0.4.0` 与 `gh release create`，理由：用户未明确授权这次将 APK、源码归档与校验文件公开发布到 GitHub 的具体目的地。命令没有执行，不能绕过审批或改途径上传。
- 功能代码与通用文档按 AGENTS 的既有约定已推送 main；这与版本附件公开发布分别记录。本地可安装 APK、源码、SHA256SUMS、使用/验收说明及预览均已交付。
- 如用户明确同意在 https://github.com/lukino0737/daybook/releases 公开发布 v0.4.0，再核对 Git 状态、现有 CI 与已签名输出，创建新标签和 Release 并校验远程附件。无需重新开发、构建或完整 QA；不要覆盖旧标签。

## 验证与限制

- Debug/测试/Release 构建通过，39 项 JVM 单测通过，Lint 0 错误。
- A 筛选重置 1 项；B 分页与实际日历 2 项、节假日 2 项；C 便签/通知/重建/备份回滚/并发状态/历史迁移共 7 项专项通过。
- D 外观存储、失败保护、照片方向/缩放、配色对比度、预览应用恢复共 4 项通过；四主页正常字体 1 项、150% 字体 2 项通过并目视检查；另 150% 字体加软键盘便签专项 1 项通过。
- 真实已发布 v0.3.0 → 正式 v0.4.0 覆盖升级通过，原记录全部字段和通知渠道设置保留，真实 3→4 迁移成功；正式包启动 1 项、便签通知直达/编辑重建 1 项通过。
- 正式包系统图片选择器选图、未应用预览、应用后重启保留与恢复默认通过，截图 work/v04/release-picker-*.png。
- A/B CI 34751483037、C CI 34751845966、D CI 34761242134 成功。交付代码 8e03e3b 的 CI 34762134277 成功。
- 失败记录：B 首次 scrollToPage 触发列表子布局测量重入，换 requestScrollToPage 后相关复测通过；C 编译空值问题及误运行旧测试包的类缺失已修正，重建后的 7 项为有效结果。升级脚本首次把未安装返回码当错误，尚未安装就退出，修正后通过。两次模拟器启动审批超时重试成功。
- 既有提醒异常需更久实测，用户要求暂缓；跨品牌后台送达、声音/振动/悬浮、Android 8–12 真机未实测。低版本解码分支在 API 35 专项调用通过，不等于旧系统真机通过。用户 Redmi K70 Pro 实测开启自启动后，默认智能省电与无限制都可划后台提醒；关闭自启动仅回前台补发，不能推广为所有机型结论。
- 官方调休/清明资料覆盖 2025/2026，未覆盖年份不猜测放假补班。现行提醒仅过去不足 24 小时补发，晚于 60 秒标注补发；系统强停后须重新打开。其他 backlog 未获准实施。

## 交付物与证据

- APK SHA-256：`272a63c177889f63be663be17bd7a3baeefb0028422514e60be7cef05a460911`；签名证书 SHA-256 与 v0.3.0 相同，校验记录 work/v04/release-verification.json。
- 正式产物 outputs/daybook-v0.4.0.apk、SHA256SUMS-v0.4.0.txt、daybook-v0.4.0-source.zip、使用说明-v0.4.0.md、验收报告-v0.4.0.md；用户可点击副本放在当前 Codex 任务 outputs，最终回复只链接该目录。
- docs/USAGE.md、TESTING.md、DEVELOPMENT.md、V0.4-PLAN.md、releases/v0.4.0.md；docs/screenshots 的日历/回顾/便签/外观为虚构样例，原 v0.3.0 发布标签与输出保留。
- 本轮证据 work/v04/a-*.log、b-*.log、c-*.log、d-*.log、memo-large-ime.log、release-build.log、upgrade.log；预览 work/v04/previews。首启验证复用 work/startup-*.log，旧发布验收详见 TESTING 历史节。

## 重要文件与环境

- app/src/main/java/dev/lukino/daybook：data/Memo.kt、DaybookDatabase.kt/schema 4、EntryRepository.kt；ui/MemoController.kt、MemoEditor.kt、AppearanceSettings.kt、Theme.kt、MonthCalendar.kt、DaybookScreen.kt；appearance/AppearanceStore.kt、ImageColors.kt；reminder/ReminderTarget.kt、ReminderCoordinator.kt；backup/BackupCodec.kt、BackupService.kt。
- work/v04/build.sh 使用既有 JDK 21、Gradle 缓存与 SDK；不重新安装环境。Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Compose BOM 2025.12.01、Room 2.8.4，Android 8+ 单模块离线架构。
- 原专用 Daybook_API_35：ANDROID_AVD_HOME=$PWD/work/avd，已关闭，保留 QA 数据及 v0.4.0 QA 包。新独立升级实例：ANDROID_AVD_HOME=$PWD/work/v04/upgrade-avd，同名 AVD 但独立空白数据目录，已关闭，保留正式 v0.4.0 包及验收后的默认外观。不得卸载清空原实例以便重跑升级。
- work/v04/verify_upgrade.py 仅用于全新升级验收实例；运行前断言未安装 Daybook。work/v04/ui.py 驱动独立实例系统选图；work/startup-qa/install_test.py 只做同签名 QA 覆盖。
- work/github_cli.py 复用现有认证，不打印密钥。签名材料 .local/signing/daybook-release.jks 与 keystore.properties 保持忽略，不能提交或输出。work、outputs、本地配置、构建产物不入 Git。
- 当前任务 cwd 与实际工程目录不同，实际工程仍是用户最初给定目录；写权限按回合申请，Git/ADB/Gradle 本地通信另需工具审批。

## 协作与恢复

宏观方案先说明推荐、替代方案及利弊并获确认；v0.4 已整体批准，不重复确认内部细节。阶段提交推送 main，不强推、不移动旧标签，不自动扩展 backlog。用户只让记下时不实施。

长任务适度查额度：高于 10% 正常，5–10% 收尾当前阶段，低于 5% 安全暂停并维护本文件，不擅自消耗重置信用。恢复先读本文件、AGENTS、Git 状态/最近日志和相关现有证据；完成且通过部分直接复用。
