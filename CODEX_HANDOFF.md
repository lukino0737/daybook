# Daybook 恢复记录

- 当前目标：用户批准的 v0.3.0 改动已完成并发布，等待用户手机试用反馈。
- 已完成：A 固定底部导航、紧凑滑动日历、任务分区、日程命名与日期规则、显式回顾筛选（3a92819）；B 内置官方 2025/2026 假期/补班及常用节日（036408a）；C 独立提醒设置页、既有渠道保留、新渠道系统声/振动/高重要性、删除冗余与手动按钮（1382f83）。均已推送 main。
- 版本：0.3.0 / code 3，Room schema 3 / JSON 3 不变，同签名沿用旧版本。没有数据库破坏性迁移。
- 已验证：32 项 JVM 单测，19 项常规模拟器用例；权限拒绝、150% 字体、真实 v0.2→v0.3 覆盖升级通过。Debug/Release/Lint 通过。Release 全新安装 UI 与渠道配置通过；后台进程退出后实际通知与顶部悬浮通过，已发送状态核对通过。
- 失败记录：大字体回顾测试点击合并卡片中心触发了标签，改为点击标题本身后复测通过。详情见 docs/TESTING.md，未将失败或跳过记作通过。
- 交付物：outputs/daybook-v0.3.0.apk、outputs/SHA256SUMS-v0.3.0.txt；SHA-256 1fdd6fedb3fb99ccb720d3f03eaf3fda94bad763eef8748edab0db050e875fa1。README 三张截图、USAGE、BUILD、TESTING、BACKLOG 和发布说明已同步。
- 发布：v0.3.0 标签指向 827709a3d132b3b255f1107c8a3700fa0b1e7c72；GitHub CI 34560985624 通过，APK/校验附件远程 digest 与本地一致。https://github.com/lukino0737/daybook/releases/tag/v0.3.0
- 收尾：虚构验收记录已清理，测试 APK 已卸载，专用模拟器已停止。源码归档、使用说明与验收报告位于 outputs/。
- 下一步：无未完成实现；等用户试用反馈。若继续，先核对本记录和 Git 状态，不重复构建或验收。新宏观方案需先确认。
- 证据：work/v03-reminder-build.log、work/v03-regression-device.log、work/v03-large-font-retest.log、work/v03-release-build.log、work/v03-upgrade.log、work/v03-release-ui.log、work/v03-lifecycle-*.log。截图 docs/screenshots/；实际悬浮 outputs/v03-heads-up.png。
- 工具：JAVA_HOME=$PWD/work/tooling/jdk-21.0.12.1.jdk/Contents/Home；GRADLE_USER_HOME=$PWD/work/gradle-user；ANDROID_USER_HOME=$PWD/work/android-user；ANDROID_HOME=$PWD/work/android-sdk。GitHub 使用 python3 work/github_cli.py。work/verify_upgrade_v03.py 已完成升级验证，不需重跑。
- 重要限制：官方调休表和清明仅覆盖 2025/2026；真机声音/振动/静音与厂商省电策略未验证。教程、生日循环、照片、背景和同步留后续，新增宏观方案先请用户确认。
- 数据安全：签名仍在忽略的本地配置，禁止输出凭据。不要提交真实记录、签名、构建产物；只操作专用模拟器 Daybook_API_35，不清理用户手机数据。
