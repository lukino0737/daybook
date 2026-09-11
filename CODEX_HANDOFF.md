# Daybook 恢复记录

- 当前目标：实施已确认的 v0.3.0 界面、筛选、节假日及提醒改动，方案见 docs/V0.3-PLAN.md。
- 授权：用户 2026-09-11 确认节假日 APK 内置、保留现有通知渠道，可以开始。无需重复询问已确认方案。
- 起点：v0.2.0 已发布，main 最近稳定 bcb27ff；原有两处未提交文件是反馈记录，已合并到当前工作。
- 当前阶段 A：底部三入口、紧凑日历/左右滑动、任务分区、“日程”名称及叉号清除任务日期、回顾显式筛选已实现并验证。代码提交 3a9281911f4dacebf8496a61bceda1c3264fa471，已推送并核对远程 main。
- 已验证：30 项 JVM 单测通过；15 项常规仪器用例通过（首轮日历标记定位失败，work/v03-calendar-test.log 复测通过）；3 项条件用例跳过。Debug/Lint 通过，outputs/v03-calendar-draft.png 已目视检查。
- 阶段 B 已完成：内置官方 2025/2026 调休表、常用公历/农历节日、三色日历标记与日期详情。资料见 docs/HOLIDAY-SOURCES.md。32 项 JVM 单测、3 项针对性模拟器用例、Debug/Lint 通过；B 提交 036408a 已推送核对。
- 阶段 C 功能已完成：独立提醒页、既有渠道保留、新渠道声音/振动/高重要性、删除冗余与手动按钮，自动核对保留。32 项单测、19 项常规模拟器、拒绝通知专项通过；150% 字体 3 项通过（回顾点击标题定位修正后复测）。
- 剩余：最终截图、文档、同签名升级、v0.3.0 发布。README/USAGE 已开始同步，尚未提交；版本号仍是 0.2.0。当前日志 work/v03-regression-device.log、work/v03-denied-device.log、work/v03-large-font-device.log 和 work/v03-large-font-retest.log。模拟器已恢复字体 1.0。
- 数据：数据库与备份结构未改变，EVENT 仅中文显示名变为日程，不改稳定标识。禁止清理用户数据或提交真实记录/签名材料。
- 重要路径：app/；docs/V0.3-PLAN.md；work/v03-build.log、work/v03-tests.log；既有工具 work/；旧版 APK outputs/daybook-v0.2.0.apk；签名仍在忽略的本地文件。
- 额度：2026-09-11 恢复时五小时剩余 99%、周剩余 100%，继续正常执行。不重复已通过的 A 验证。
- 构建环境：JAVA_HOME=$PWD/work/tooling/jdk-21.0.12.1.jdk/Contents/Home；GRADLE_USER_HOME=$PWD/work/gradle-user；ANDROID_USER_HOME=$PWD/work/android-user；ANDROID_HOME=$PWD/work/android-sdk。GitHub 操作可用 python3 work/github_cli.py；签名凭据不得输出。
- 已知限制：真实手机通知声音/振动/悬浮仍待验证；现有渠道声音振动设置须由用户在系统管理。教程不在本版。
