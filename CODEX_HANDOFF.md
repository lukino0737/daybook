# Daybook 项目交接

更新：2026-09-21。**v0.6 第一批三个里程碑的实现与本地验收已完成，最后待核对第三里程碑推送后的CI。** 用户已批准本批范围；本批仅代码、验收、推送main与CI，不交付安装包、不公开发布。已发布版本仍为v0.5.0/code6，其标签和附件保持不变。

## 当前目标与完成范围

- 开发版本0.6.0-dev/code7，Room5/JSON5，单Android模块，无新依赖。
- 独立单次/重复提醒：每天、每周多选、每月、每年公历、每隔1–9999天。缺失日期跳过，固定原始日期锚点；24小时内最多补最近一次，满24小时排除。暂停期间不补发，恢复或改规则从当前时刻起算；改标题/备注/日历不重发。
- 新建记一笔第四入口“提醒”；便签页加号仍创建便签；新建类型互切保留标题/备注。显式保存/取消、失败保留草稿、暂停恢复、确认删除整条。
- 菜单统一提醒列表：独立提醒和事项/便签的一次提醒各显示一行，待提醒按下次排序，已暂停/已结束默认折叠按修改时间排序。附属提醒打开原内容。
- 按所查看月份计算日历发生项，普通事项后单列提醒分区；年度默认开启，其他关闭，用户手动优先。暂停不改变日历显示。不生成海量记录、不影响任务数量/逾期/回顾。
- 通知目标daybook://reminder/{id}接入；真实通知可打开编辑页，已删除目标提示并返回列表。已有编辑草稿先保留，保存或取消后再打开通知。重建保留未保存输入。
- 自启动/无限制引导、电池设置入口、新安装与旧安装首次使用提醒的一次性说明完成，不猜测厂商开关状态、不重复请求已拒绝的通知权限。
- Room4→5增量迁移和完整历史迁移；JSON1–5兼容。三类数据校验、数量预览、替换前完整快照、事务替换，旧备份清空提醒的提示；失败不影响原数据。

## Git与CI

- 第一里程碑df9f149已推送，CI35557870291成功，work/v06/m1-ci-final.json。
- 第二里程碑0f330ce已推送，CI35558363397成功，work/v06/m2-ci-final.json。
- 本次开始main/origin/main为707c101，三个未提交草稿已复用、修正、接入并完成验收，不再是待实现文件。
- 第三里程碑及最新交接提交号用git log读取；推送后CI结果在work/v06/m3-ci-*.json记录。若后文已补充成功结果则无需重复等待。
- 不强推，不移动v0.5.0标签，不触碰发布附件。

## 已验证结果

### 规则、数据、调度（前两阶段已通过，直接复用）

- M1：49项JVM、Lint、Debug/AndroidTest构建；API35专用模拟器8项迁移、备份、事务失败和草稿状态验证通过。初次设备7过1失败，修复旧草稿发送状态校验后全组8通过。日志m1-final-build.log、m1-final-device.log。
- M2：49项JVM及构建/Lint；API35常规7项、外部撤权/恢复各1项、实际重启seed/verify各1项通过。外部驱动在未打开App前确认通知已出现，避免打开App补发掩盖重启调度。日志m2-acceptance.log、m2-reboot-notification.txt等。
- Android撤销精确闹钟权限会终止应用，权限测试已使用外部驱动，不在同一测试进程内撤权后继续断言。

### 界面与日历（第三阶段）

- 最终53项JVM，失败/错误/跳过均0；Lint0错误、Debug/AndroidTest构建通过。work/v06/m3-final-layout-build.log。
- API35正常字号19种场景分批通过：StandaloneUiTest5、StandaloneNotificationUiTest1、BackupUiTest3、InteractionTest6、TaskListsUiTest/MemoUiTest/ReminderUiTest/MemoNotificationTest各1。
- 原m3-normal.log组合18通过1失败（旧日历删除用例点击删除后未找到确认框）；同一用例未修改即在m3-calendar-isolated.log独立通过；最后日历分区局部调整后m3-calendar-final.log三项复验通过，也包含该旧用例。**不得写成首次19项一次性全过，不据此声称已确定失败根因。**
- 150%字号3项编辑/列表/失败/删除/日历操作通过（m3-large.log），最终日历局部调整后大字号另1项通过（m3-calendar-large-final.log）。
- 真实通知PendingIntent直达、页面重建保留草稿、取消不改原文、删除目标提示、通知等待当前编辑完成、原事项/便签路由通过。
- 已目视检查正常和150%字号截图：编辑、选项、列表、保存失败、删除确认。截图只含虚构样例；采集等待原生窗口淡入结束，初期淡入帧不是最终视觉证据。work/v06/previews-final/保存最终截图副本。
- 新增控制器失败提示不展示底层数据库错误；修复规则切换时输入间隔与保存值不一致、已发送单次提醒预览和状态栏文字对比度。

## 未完成、已知限制与边界

- 本批实现与本地验收无尾项；仅在第三阶段CI尚未记录成功时完成推送/CI确认及文档收尾。之后停止，等待用户新需求。
- **不交付安装包、不公开发布；正式签名包覆盖升级未验证。** 内部匹配签名QA覆盖安装不等于正式包升级验收。
- 鸿蒙可安装是用户反馈；本轮未做鸿蒙或跨品牌真机专项。既有长期提醒异常不扩展排查，不宣称所有机型送达已保证。
- 旧日历UI组合测试出现过一次确认框未出现，独立和最终专项复验通过；保留日志供以后出现同类问题时定位，不反复跑完整QA。
- CI原有Node/action弃用提示未处理，不属于本批。
- 图片、隐私便签、拖动排序、农历、重复任务、多时段、提前提醒、单次例外均不在本批；不自动开工。

## 重要文件

- 范围/验收记录：docs/V0.6-M1.md、docs/TESTING.md、docs/DEVELOPMENT.md、docs/USAGE.md、README.md。
- 数据与规则：data/StandaloneReminder.kt、DaybookDatabase.kt、EntryRepository.kt；reminder/RepeatRules.kt、ReminderListRules.kt、ReminderTarget.kt、ReminderCoordinator.kt；backup/BackupCodec.kt、BackupService.kt。
- 界面：ui/StandaloneReminderController.kt、StandaloneReminderEditor.kt、ReminderListScreen.kt、DaybookViewModel.kt、DaybookScreen.kt、EntryEditor.kt、MonthCalendar.kt；MainActivity.kt为通知路由。
- 测试：RepeatRulesTest、ReminderListRulesTest、StandaloneStorageTest、StandaloneDeliveryTest、StandaloneLifecycleTest、BackgroundGuideTest、StandaloneUiTest、StandaloneNotificationUiTest以及受影响旧测试。
- 本地证据work/v06/；既有work/v05/和outputs/正式v0.5成果保持不变；这些目录被Git忽略。

## 环境与恢复

- 项目位于 /Users/lukino/Documents/Codex/2026-09-09/ai-coding-ai-coding-ai-coding。
- 先读本文件、项目AGENTS.md与/Users/lukino/.codex/AGENTS.md，低成本核对Git、现有日志/输出，复用已验证成果。
- 本机/usr/bin/git默认选中Xcode时提示未同意许可。使用命令级 DEVELOPER_DIR=/Library/Developer/CommandLineTools 调用Git及work/github_cli.py即可；未修改系统许可或安装环境。
- work/v04/build.sh封装既有JDK/SDK/Gradle；构建需要沙箱外本地套接字。work/github_cli.py复用认证，勿打印凭据。
- 专用API35 ARM64模拟器work/avd/Daybook_API_35.avd，emulator-5554；本轮重新启动，测试完成后恢复原字号。是否已关闭见最终补充或核对实际状态，不卸载/清库。
- work/startup-qa/install_test.py仅为已有QA安装制作匹配签名的内部测试包并覆盖安装，不交付/发布。不要输出签名密码。
- 旧升级实例emulator-5556本轮未操作。不可重跑旧脚本中断言当前必须为v0.4.0的升级场景。
- 额度按最新个性化说明，只在自然阶段检查。此次恢复时五小时额度已重置且充足，未使用重置信用；不要沿用上一轮“剩余27%”作为当前限制。
