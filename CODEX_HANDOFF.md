# Daybook 项目交接

更新：2026-09-22。**v0.6.0已正式发布，当前工作全部完成。停止实施，等待用户新需求。**

## 本次交接检查与未完成工作

- 本轮仅整理交接，没有新功能、重构、构建、模拟器启动、完整QA或重新发布。
- 实际分支main；交接开始时HEAD与本地origin/main均为4413e9ce75cd9653f440f548d00ce06e2f79681a，工作区干净。该提交是发布完成记录；最近稳定业务提交为e6818e79dde42395db50df78a578363cd7e98461（v0.6.0）。二者之间仅三份文档不同。
- 已读取现有构建、三项任务/提醒专项、正式升级/启动日志及JVM/Lint报告；本地三个发布附件重新计算的大小和SHA256均匹配work/v06-release/published-release.json中的远程发布证据。本轮未重新请求远程发布接口，不把保存的历史证据描述为新一次远程检查。
- **正在进行但未完成的功能、测试、发布：无。** 开始交接时没有遗留未提交修改；本轮仅更新本文件与docs/DEVELOPMENT.md，完成后做独立文档提交并推送main，最终提交号以git log读取。

## 已批准的重要设计决策

- 单Android模块，Compose UI / ViewModel / Repository / Room分层；离线、无账号，不新增网络、云存储、同步或外部依赖。
- 日期采用本地日历语义；只有任务可完成和逾期，新任务默认无截止日期；日程和记录仍要求发生日期。
- 事项显式保存/取消，便签自动保存；图片位于正文内，独立提醒不支持图片。图片数量、尺寸、压缩和格式沿用已实现的默认规则，见下方图片批次记录。
- 数据库使用保留原数据的增量迁移并保留历史schema。备份先完整校验、预览确认、创建完整恢复快照，再事务替换；失败不能破坏原数据。
- 后续会改变方向、重要功能、架构、数据格式或依赖的宏观方案，先列推荐与替代方案、优缺点、风险、影响和实施计划，经用户明确确认后实施；批准范围内的小实现细节自主处理。
- 每个已批准里程碑更新开发记录，提交推送main并确认CI；后续正式发布仍需用户授权，不因功能完成自动发布。

## 已取消与不采用的方案

- 隐私便签整项取消：下滑隐藏入口、密码解锁、加密库、密码恢复等都未实施。此前方案仅供讨论，不代表批准，不得恢复为待办。
- 便签长按拖动排序取消；“图片→排序→隐私”和“图片→隐私”的后续路线均不再适用。
- 图片附件区方案未采用，采用正文内插图；不扩展到独立提醒，不添加云存储/同步。
- 原始需求文件仅作背景，不是对其全部条目的实施授权；不根据历史交接中的“下一批”自动开工。

## 已知问题、风险与待解决事项

- 旧日历删除确认框用例在组合测试中偶发失败，未修改同一用例的独立复验及最后日历专项通过；根因未确定。本轮没有复现或修复，不标记为已解决。
- 长期提醒异常仍待用户长期实测，当前暂停调查；各品牌后台策略可能影响送达。鸿蒙可安装仅为用户反馈，鸿蒙6及跨品牌真机未验证。
- Lint有30项警告、0错误；CI仍提示部分action/Node版本弃用。均未在本轮扩大维护范围。
- 正文图片及备份没有密码加密；旧版不能读取新ZIP图文备份。恢复不含新增类型的旧备份可能移除当前对应数据，需保留确认提示和完整回滚快照。
- 已在API35模拟器验证真实v0.5→v0.6原签名覆盖升级，不等于所有历史版本或所有真机均验证通过。

## 下一阶段建议与优先级（建议不等于实施授权）

1. 最高优先：保持当前已发布稳定状态。新对话完成低成本接手检查后，简报并等待用户新的具体需求；当前无需继续开发或补跑验收。
2. 如用户反馈可复现问题，先保存最小复现与环境信息，再决定针对性修复；长期提醒调查仅在用户明确恢复时继续，禁止自动开启长期监测。
3. 如用户提出新功能，再结合docs/BACKLOG.md讨论范围和替代方案；不重启已取消的隐私/排序，不擅自定下一版本或发布计划。

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
- 项目目录：/Users/lukino/Documents/Codex/2026-09-09/ai-coding-ai-coding-ai-coding。先检查git status、最近提交、HEAD与origin/main及v0.6.0标签；再按需查看work/v06-release/的ci-final.json、published-release.json和验收日志、outputs/三个v0.6发布产物。若出现新的未提交修改，先识别来源，不覆盖、不重置。
- app/src/main/java/dev/lukino/daybook/是下方源码相对路径的根目录；测试分别位于app/src/test/与app/src/androidTest/，历史Room结构在app/schemas/。work/和outputs/为本机证据/交付目录，被Git忽略；换机器若缺失，不伪称已检查这些本地证据，应从已提交验收文档及正式发布核对。
- Git使用命令级DEVELOPER_DIR=/Library/Developer/CommandLineTools，绕开默认Xcode许可提示；不修改系统许可。
- work/v04/build.sh封装已有JDK/SDK/Gradle。work/github_cli.py复用既有认证，勿输出凭据。
- 通用专用AVD位于work/avd/，端口5554；升级专用AVD位于work/v04/upgrade-avd/，端口5556。升级AVD现在已是v0.6.0，不可再运行断言必须为v0.4或v0.5的旧seed脚本，更不可降级/卸载以重跑。
- work/startup-qa/install_test.py是内部QA覆盖安装工具，不等于正式发布包；本次正式升级使用work/v06-release/verify_upgrade.py，已完成，不重复运行seed。
- 额度仅在自然阶段检查，未使用重置信用；接手不要沿用旧百分比。
