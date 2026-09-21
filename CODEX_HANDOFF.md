# Daybook 项目交接

更新：2026-09-21。**v0.6 第一批正在实施，尚未完成。** 用户已明确批准本对话中的正式计划，恢复后直接继续批准范围，无需重新确认宏观方案。v0.5.0/code6此前开发、验收、CI与发布均完成，不重建或改动其标签/附件。

## 当前目标和授权

实现独立单次/重复提醒、统一提醒列表、可选日历显示、自启动与无限制引导。开发版本0.6.0-dev/code7，单Android模块，无新依赖。只做代码、针对性验收、提交推送main和CI；**不交付签名安装包、不公开发布**。图片、隐私、排序、农历、重复任务、提前提醒、多时段、单次例外均不在本批。

用户确认的关键规则：单次/每天/多选星期/每月/每年公历/每隔1–9999天；缺失日期跳过（不是月底替代）；过去24小时内最多补最近一次（满24小时排除）；暂停期间不补发；恢复或修改规则从当前时刻安排未来发生项；改标题/备注/日历开关不重发。年度默认日历开启，其他关闭，手动选择优先。暂停只停止通知。现有事项和便签仍只支持一次提醒。

正式计划的详细正文见本对话用户“PLEASE IMPLEMENT THIS PLAN”；概要及验收预期见docs/V0.6-M1.md。

## 已完成

### 里程碑一：规则与数据

- 提交df9f149（已推送main），CI35557870291成功，证据work/v06/m1-ci-final.json。
- 新增StandaloneReminder、RepeatKind及纯日期RepeatRules（最近、下一次、范围日期）。保留原始日期锚点，支持缺失日期跳过和本地时间/DST。
- Room5新增reminders表及4→5迁移，保留历史迁移；schema5已提交。
- JSON5增加独立提醒，兼容1–4。Repository三类数据写锁、快照、校验与事务恢复；恢复预览包含数量和旧备份清空提醒的提示。
- saveReminder以持久化发送状态为准，旧草稿不能覆盖发送状态；修改规则和恢复会推进revision/effectiveFrom。改计划后旧草稿提交会报错。
- 49项JVM、Lint、Debug/AndroidTest构建通过；API35模拟器8项MigrationTest/StandaloneStorageTest/MemoStorageTest/BackupServiceTest通过。
- 初轮8项中1项发现编辑时间时草稿携带旧发送状态导致校验失败；已修复并全组复验通过，不将历史失败写成一次通过。

### 里程碑二：调度与后台引导

- 提交0f330ce已推送main；CI35558363397已成功，证据work/v06/m2-ci-final.json。
- 原最近一次精确闹钟调度接入reminders流；来源/计划版本/发生时间构成独立提醒通知身份，已发出通知不因下一次时间推进而立即取消。
- 暂停、改计划取消旧通知，标题和日历改动不重发。每条独立提醒只保留最近通知。
- 后台设置新增电池策略入口及无限制说明。新版首次引导和旧安装首次使用提醒的一次性补充说明完成，不重问通知权限，不猜厂商开关状态。
- 49项JVM、Lint、Debug/AndroidTest通过。API35模拟器7项发送/旧行为/引导通过，外部撤权及恢复各1项通过，重启seed/verify各1项通过。
- 重启验证由外部驱动先观察到系统通知，再启动verify，未让打开App补发掩盖重启调度。证据work/v06/m2-acceptance.log及m2-reboot-notification.txt。
- 最初在instrumentation内撤销精确闹钟权限会被Android终止进程；现改为外部驱动撤权后再启动测试，恢复后另一次测试，全部通过。
- **通知直达新编辑页尚未接入，留在里程碑三一起验收。**

## 当前未提交工作：里程碑三草稿

目前仅写出以下三个新文件，**尚未接入主页面、编译或测试**，不得宣称里程碑三完成：

- app/src/main/java/dev/lukino/daybook/ui/StandaloneReminderController.kt：SavedStateHandle草稿、显式保存/取消、删除、重复规则切换和日历手动选择。
- app/src/main/java/dev/lukino/daybook/ui/StandaloneReminderEditor.kt：标题/备注、日期/时间、规则、多选星期、间隔天数、下一次预览、启用和日历开关、删除确认及引导。
- app/src/main/java/dev/lukino/daybook/reminder/ReminderListRules.kt：统一列表行、待提醒/暂停/结束状态及排序。

源码已保存，无运行中的构建（以恢复时进程为准）。这些草稿可能还有编译/行为问题，先复用并检查，不重新设计宏观方案。

## 尚未完成与下一步

1. 前两里程碑已推送且CI成功，恢复只需核对Git和已有结果，不重新运行。
2. 编译检查三个草稿；新增ReminderListRules的真实行为单测，必要时修复。
3. 在DaybookViewModel实例化StandaloneReminderController，接入提醒列表显示状态、新建时从原事项草稿切换到独立提醒，以及新提醒切回原三类型时保留标题/备注；已有条目不跨独立类型转换。
4. 新建EntryEditor增加“提醒”入口；便签页加号仍创建便签。菜单新增“提醒列表”，列表另有新增和提醒设置按钮，底部四项导航不变。
5. ReminderListScreen：统一来源列表，待提醒按下次升序，暂停/结束折叠按最近修改倒序；每条来源一行；附属提醒点击原编辑页，独立提醒点击新编辑页。
6. MainActivity接入daybook://reminder/{id}，目标删除时提示并返回提醒列表；不得破坏现有entry/memo路由或未保存草稿。
7. MonthCalendar按各pager月份动态计算showInCalendar提醒日期，添加事项点及无障碍数量；当天列表单独提醒分区。暂停仍显示、编辑整条、不进入任务计数/逾期/回顾。不要预生成数据库发生项。
8. 增加专用模拟器UI测试：创建/保存/失败/取消/删除确认/暂停恢复/手动日历选择不覆盖/来源跳转/通知直达/目标删除/旧备份预览/页面重建；正常和150%字号截图目视检查。复用v0.5无关外观成果，针对性回归任务/便签/一次提醒。
9. 运行本批相关JVM、Lint、Debug/AndroidTest及设备测试；更新USAGE、TESTING、DEVELOPMENT、V0.6-M1和交接，检查暂存后提交推送main并确认CI。
10. 完成后停止，不发布。正式签名包覆盖升级不在本批，需明确记录未验证。

## 验证和重要输出

- work/v06/m1-final-build.log：49项JVM/构建/Lint；m1-final-device.log：8项通过；m1-ci-final.json：成功。
- work/v06/m2-final-build.log：构建/JVM/Lint；m2-final-device.log：7项通过。
- work/v06/m2-denied.log、m2-granted.log：外部撤权/恢复。
- work/v06/m2-lifecycle-seed.log、m2-lifecycle-verify.log、m2-reboot-notification.txt：真实重启调度。
- work/v06/run_m2.py：本轮实际验收驱动，已成功，不需要无故重跑。
- 已有JVM结果app/build/test-results/testDebugUnitTest，设备测试输出均与真机分开。

## 环境与运行状态

- 项目路径 /Users/lukino/Documents/Codex/2026-09-09/ai-coding-ai-coding-ai-coding。
- work/v04/build.sh封装现有JDK/SDK/Gradle。Gradle在沙箱内因本地套接字Operation not permitted失败；已用require_escalated运行成功，不重装依赖。
- QA专用API35 ARM64实例work/avd/Daybook_API_35.avd，端口emulator-5554，本轮启动并重启验证，当前可能仍运行；先核对。不要卸载/清库或操作其他设备。
- work/startup-qa/install_test.py复用本地签名为现有QA安装制作内部匹配签名测试包后覆盖安装，未交付或发布。不要输出签名文件或密码。
- work/github_cli.py封装GitHub认证，勿打印凭据。Git推送与CI读取需网络权限。
- 旧升级实例emulator-5556本轮未操作；v0.5发布成果outputs/及work/v05保持不变。

## 已知问题与限制

- 里程碑三尚未完成，当前正式可用发布仍为v0.5.0；main中新增数据/调度但还无独立提醒完整用户入口。
- 鸿蒙可安装为用户新反馈，本轮不做鸿蒙适配或真机专项。跨品牌长期提醒问题不扩展排查，不承诺系统后台设置能保证所有机型送达。
- 新开发内部测试包不能视为正式签名包覆盖升级通过。
- CI原有Node/action弃用提示不在本批。
- 单Android模块、本地日期、仅任务完成/逾期；非破坏性迁移；备份完整校验、预览确认、先快照后事务替换等项目规则继续生效。

## 额度与恢复

本轮在进入页面里程碑的自然节点检查：五小时额度已用73%、剩余约27%。完整页面接线与设备验收仍需较多工作，按最新个性化说明停止开启新的高成本工作，先保存本交接。未使用重置信用，不为精确监控频繁查询额度。

恢复先读本交接、AGENTS.md、/Users/lukino/.codex/AGENTS.md和实际Git状态。从三个已保存草稿继续，不重跑已成功且未受改动影响的M1/M2全套测试。用户已批准实施方向，仅宏观范围变更需再次确认。

## 安全收尾实际Git状态

2026-09-21：main和本地origin/main均为0f330ce。仅CODEX_HANDOFF.md已修改，另有上文三个里程碑三新文件未跟踪；这些文件均已保存但未编译，不能提交为已验收功能。交接文档若随后提交，其提交号以git log -1为准。

最终低成本核对：第二里程碑CI35558363397成功，headSha匹配0f330ce；交接提交b19b6dd已推送，工作区仅有三个未验证新文件。随后仅补充CI成功记录。
