# Daybook 恢复记录

- 当前目标：实施已确认的 v0.3.0 界面、筛选、节假日及提醒改动，方案见 docs/V0.3-PLAN.md。
- 授权：用户 2026-09-11 确认节假日 APK 内置、保留现有通知渠道，可以开始。无需重复询问已确认方案。
- 起点：v0.2.0 已发布，main 最近稳定 bcb27ff；原有两处未提交文件是反馈记录，已合并到当前工作。
- 当前阶段 A：底部三入口、紧凑日历/左右滑动、任务分区、“日程”名称及叉号清除任务日期、回顾显式筛选已实现并验证。代码提交 3a9281911f4dacebf8496a61bceda1c3264fa471，已推送并核对远程 main。
- 已验证：30 项 JVM 单测通过；15 项常规仪器用例通过（首轮日历标记定位失败，work/v03-calendar-test.log 复测通过）；3 项条件用例跳过。Debug/Lint 通过，outputs/v03-calendar-draft.png 已目视检查。
- 阶段 B 仅完成官方公告核对，资料已保存 docs/HOLIDAY-SOURCES.md，尚未编写节假日代码。
- 剩余：从 B 节假日实现开始；然后 C 独立提醒权限页面/默认振动声音/删除冗余，最终截图、文档、同签名升级、v0.3.0 发布。版本号仍是 0.2.0，不要将当前 Debug 当作最终新版发布。
- 数据：数据库与备份结构未改变，EVENT 仅中文显示名变为日程，不改稳定标识。禁止清理用户数据或提交真实记录/签名材料。
- 重要路径：app/；docs/V0.3-PLAN.md；work/v03-build.log、work/v03-tests.log；既有工具 work/；旧版 APK outputs/daybook-v0.2.0.apk；签名仍在忽略的本地文件。
- 额度：安全暂停。最近检查两个窗口均剩余约 11%，不足以稳妥完成下一阶段，按用户规则保存后暂停。恢复先检查额度，不重复已通过的 A 验证。
- 构建环境：JAVA_HOME=$PWD/work/tooling/jdk-21.0.12.1.jdk/Contents/Home；GRADLE_USER_HOME=$PWD/work/gradle-user；ANDROID_USER_HOME=$PWD/work/android-user；ANDROID_HOME=$PWD/work/android-sdk。GitHub 操作可用 python3 work/github_cli.py；签名凭据不得输出。
- 已知限制：真实手机通知声音/振动/悬浮仍待验证；现有渠道声音振动设置须由用户在系统管理。教程不在本版。
