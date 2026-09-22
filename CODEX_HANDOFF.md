# Daybook 项目交接

更新：2026-09-22。**v0.6.0已正式发布，当前工作全部完成。停止实施，等待用户新需求。**

## 当前目标与授权

- 用户取消隐私便签；此前仅提出方案，没有实施加密、密码或隐藏入口。便签拖动排序也已取消，不再作为待完成批次。
- 用户授权把新任务默认改为「不设截止日期」，完成后发布v0.6.0安装包。该修改、针对性验收、真实旧版覆盖升级、提交推送、CI、发布及远程附件校验均已完成。
- 不自动启动backlog。农历、重复任务、多时段、提前提醒、单次例外等未纳入。
- 鸿蒙6及跨品牌真机未验证；既有长期提醒异常继续暂缓。

## 正式发布与Git

- 正式版本0.6.0/code7；Room6，ZIP内JSON6，兼容JSON1–5备份。
- 发布代码及标签v0.6.0指向e6818e79dde42395db50df78a578363cd7e98461。已推送main；[CI35722090962](https://github.com/lukino0737/daybook/actions/runs/35722090962)成功。
- [v0.6.0 Release](https://github.com/lukino0737/daybook/releases/tag/v0.6.0)公开、非预发布，并为latest。
- 附件daybook-v0.6.0.apk、daybook-v0.6.0-source.zip、SHA256SUMS-v0.6.0.txt已上传，远程大小/SHA256均与本地一致。
- APK为原签名，12883791字节；SHA256：60e6ac10f3115b17a51465931047cae24dfd84b988afb86a9b115aa9781a88ba。
- 源码归档来自发布提交的git archive，不含work、outputs、签名材料、个人数据库或本地配置。归档内为发布前交接快照，最新完成记录见main。
- v0.5.0标签仍为cd35b246b7f848fafc9f8d004f7017cb02d3de78，Release正文、附件ID/大小/SHA256均与旧证据一致；未替换或删除旧附件。
- 最后仅同步完成记录，最新文档提交用git log读取；业务代码和已通过CI、已验收APK保持一致。

## 本次小改动与验收

- 任务页新建、日程/记录切换为新任务、独立提醒切换为新任务，均默认无截止日期和时间，显示「不设截止日期」。可按需自行设置。
- 编辑已有任务保留原截止日期；新任务草稿经提醒页返回保留自行设置的截止日期和时间。
- 正式构建Debug/AndroidTest/Release、57项JVM通过（失败/错误/跳过均0），Lint0错误、30项警告。日志work/v06-release/build.log。
- API35三项任务/提醒专项一次通过：TaskListsUiTest两项、StandaloneUiTest类型切换一项。日志targeted.log。
- 专用升级模拟器原安装包与已发布v0.5.0 APK的SHA256完全相同；核对新旧正式签名一致后直接覆盖安装v0.6.0，无卸载/清库。
- V06UpgradeTest使用平台SQL快照全部旧列，验证全部旧事项/便签字段、现有外观文件哈希、通知渠道设置保留，Room4→6，新图片列为空且提醒表为空。seed、verify和SmokeTest正式启动各一项通过。
- 升级测试只新增并删除其自身虚构样例，原数据保留。日志upgrade-run.log、upgrade-seed.log、upgrade-verify.log、release-smoke.log；元数据release-verification.json。
- 两个专用API35模拟器均已核对字号1.0并正常关闭，数据保留；emulator-stop.log。
- CI证据ci-final.json；发布校验证据published-release.json，位于work/v06-release/。草稿按tag查询API返回过404，公开发布后已完整验证正式标签与三个附件；不据此声称草稿查询曾通过。
- 未重跑完整QA；此前提醒与图片批次的有效结果直接复用。

## v0.6已完成范围与既有证据

- 第一批：独立单次/重复提醒（每天、每周多选、每月、每年公历、每隔1–9999天）、统一提醒列表、可选日历显示、通知路由、后台引导。重复提醒补发最近24小时内至多一次，暂停期间不补发。Room4→5/JSON5。代码38df5cb，CI35577995029成功。
- 第一批53项JVM与针对性API35测试通过；正常字号19场景分批通过，原组合18过1失败（旧日历删除确认框），独立及最后三项日历复验通过。150%字号专项通过。日志work/v06/，详情docs/TESTING.md。不得声称首次19项一次性通过或日历偶发失败根因已解决。
- 图片批次：日程、任务、记录和便签正文内插图，文字—图片—文字；每条9张，静态JPEG/PNG/WebP，原图20MiB、长边2560、JPEG85或透明PNG、处理后5MiB。便签可仅图片；事项显式保存/取消、便签自动保存；图片查看/缩放/移除。
- 本地不可变图片副本、草稿/撤销引用和清理、Room5→6、完整ZIP备份/JSON6。恢复先完整校验、预览确认、创建完整快照后事务替换，失败保留原数据。旧JSON1–5兼容；旧备份没有的新类型数据在整体替换时会清空，确认页提示，原内容保存在快照。
- 图片提交1565c1c、4fe10e8、1a776fd及各CI成功。最后CI35695278214；图片阶段57项JVM、构建/Lint通过。
- 图文/存储正常字号13种场景分批通过；m3-normal-initial.log十二过一失败，撤销等待修正后m3-undo-final.log通过。完整图片解码/备份四项m3-backup-decode.log通过，含匹配SHA但截断图片拒绝恢复。
- 真实DocumentsUI选图、删除相册原图后副本可读、Activity真实重建保留内容通过（m3-system-picker-final.log）。早期窗口/点击与重复删除原图清理问题的日志保留，未据此判定产品导入缺陷。
- 受影响旧流程13种场景分批通过，原组合十二过一失败；旧日历删除用例未修改即独立通过，与第一批既有现象一致。150%字号两项通过。正常/大字号图文界面与真实重建截图已目视检查。
- 图片证据work/v06-images/；最终截图previews-final/和image-previews/，仅虚构样例。原批次不发布和正式覆盖升级未验证的历史边界已由本次授权与实际验收更新。

## 重要文件

- 当前发布说明docs/releases/v0.6.0.md；使用说明docs/USAGE.md；验收docs/TESTING.md；开发记录docs/DEVELOPMENT.md；已取消需求docs/BACKLOG.md。
- 本次修改ui/DaybookViewModel.kt、ui/EntryEditor.kt、app/build.gradle.kts；测试TaskListsUiTest、V06UpgradeTest。
- 图片data/RichBody.kt、media/BodyImageStore.kt、ui/RichBodyEditor.kt、ImageEditorController.kt、MemoController.kt；备份BackupCodec.kt、BackupService.kt；数据库DaybookDatabase.kt和历史schema。
- 提醒data/StandaloneReminder.kt、reminder/RepeatRules.kt、ReminderCoordinator.kt、ReminderListRules.kt；ui/StandaloneReminderController.kt、StandaloneReminderEditor.kt、ReminderListScreen.kt。
- 本地交付outputs/daybook-v0.6.0.apk、daybook-v0.6.0-source.zip、SHA256SUMS-v0.6.0.txt。保留全部旧版本产物。

## 恢复建议与环境

- 先读本文件、项目AGENTS.md与/Users/lukino/.codex/AGENTS.md；低成本核对Git和上述证据。没有待完成的实施/验收/发布工作，不重建、重跑完整QA或重复上传。
- Git使用命令级DEVELOPER_DIR=/Library/Developer/CommandLineTools，绕开默认Xcode许可提示；不修改系统许可。
- work/v04/build.sh封装已有JDK/SDK/Gradle。work/github_cli.py复用既有认证，勿输出凭据。
- 通用专用AVD位于work/avd/，端口5554；升级专用AVD位于work/v04/upgrade-avd/，端口5556。升级AVD现在已是v0.6.0，不可再运行断言必须为v0.4或v0.5的旧seed脚本，更不可降级/卸载以重跑。
- work/startup-qa/install_test.py是内部QA覆盖安装工具，不等于正式发布包；本次正式升级使用work/v06-release/verify_upgrade.py，已完成，不重复运行seed。
- 额度仅在自然阶段检查，未使用重置信用；接手不要沿用旧百分比。
