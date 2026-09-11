# Daybook

一款离线安卓个人日历。像纸质日历一样，统一记录日程、截止任务和生活片段。

## 当前功能

- Kotlin / Jetpack Compose / Room，Android 8.0 及以上。
- 紧凑月历、左右滑动换月、三类记录、任务分区与数量、近期与逾期截止提示。
- 不联网，不要求账号；通过系统文件选择器导出和恢复 JSON 备份。
- 日程/任务可设置独立通知提醒；标签、类型、时间段与标题/正文组合筛选。
- 内置 2025/2026 放假调休及常用节日，蓝点放假、红点补班、绿点个人事项。
- 手机竖屏界面；暂不包含自动同步、AI 录入、重复日程和照片。

## 安装与上手

[下载 v0.3.0 试用版](https://github.com/lukino0737/daybook/releases/tag/v0.3.0) · [使用说明](docs/USAGE.md) · [构建方法](docs/BUILD.md)

将 APK 下载到 Android 8.0+ 手机安装。旧版用户先导出备份，再直接覆盖安装，勿先卸载。首次打开没有预填数据。点日期再点右下角「+」；右上角菜单可以导出或恢复备份。

<img src="docs/screenshots/calendar.png" alt="Daybook 月历，展示虚构测试数据" width="320" />

<img src="docs/screenshots/review.png" alt="Daybook 按标签回顾，展示虚构测试数据" width="320" />

<img src="docs/screenshots/reminder-settings.png" alt="Daybook 独立提醒设置页" width="320" />

截图中的个人事项是虚构测试样例，不随安装包提供。节假日数据随安装包内置。

## 验证与维护

各版本的自动测试、模拟器检查和升级验证分别记录在 [验收报告](docs/TESTING.md)。提醒需要通知和准时提醒权限；各品牌手机的后台表现仍需真机试用。

每个里程碑都有独立提交。开发进度见 [开发日志](docs/DEVELOPMENT.md)，详细证据与限制见 [验收报告](docs/TESTING.md)，范围见 [第一阶段计划](docs/PLAN.md) 、[第二阶段计划](docs/PHASE2.md) 和 [v0.3 改进方案](docs/V0.3-PLAN.md)。

这是一个持续维护的 AI 辅助开发项目，可以从 [代码阅读与练习路线](docs/LEARNING.md) 开始。

## 数据

日程和生活记录不会逾期。任务的日期表示截止日期；没有具体时间时，截止日结束之后才算逾期。无日期任务位于任务列表下方。

个人数据、备份和签名密钥不进入仓库。卸载应用会清除本地数据，请先导出备份。
