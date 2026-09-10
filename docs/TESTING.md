# v0.1.0 验收记录

日期：2026-09-10。设备：专用 Daybook_API_35 ARM64 模拟器，Android 15，1080×2400 / 420 dpi。用户真机未连接。

| 检查 | 结果 |
|---|---|
| Debug / Release 构建 | 通过 |
| EntryRulesTest：7 项 | 通过；日期、精确时间、无日期、完成状态、跨年与闰日 |
| MonthCalendarTest：2 项 | 通过；2024–2030 每个月周对齐与闰日 |
| BackupCodecTest：6 项 | 通过；往返、空备份、异常字段、重复 ID、缺字段、大小限制 |
| 常规模拟器测试：8 项 | 通过；启动、持久化、SQL 失败回滚、草稿重建、编辑 ID、月历导航、备份服务和确认界面 |
| 系统字体 150% + 软键盘 | 专项测试通过，保存入口保持可见；检查编辑页与状态栏 |
| Android Lint（debug / release） | 通过 |
| 覆盖安装 | 通过；同签名测试基包 → release 包后完整保留测试记录，再删除测试样例 |
| Release 启动 | 通过；在 release 包上运行启动断言 |
| APK 签名 | apksigner 验证通过，RSA 3072，APK Signature Scheme v2 |
| APK 网络权限 | 未声明 INTERNET |

覆盖安装测试是 `UpgradeContinuityTest` 的 seed / verify 两次执行，常规套件中默认跳过，避免依赖执行顺序。模拟器测试数据使用专用数据库或在结束时移除自己的样例。

## 故障验证

- 快照创建回调抛错，数据库不变。
- SQLite 触发器让恢复插入失败，之前的清空操作一起回滚。
- 文件损坏、未知版本、非法日期、重复 ID、缺字段在替换前被拒绝。
- 空备份显示清空提醒；取消不更改数据，确认后可通过快照恢复。
- 备份恢复后，旧的删除撤销不能污染新数据集。

## 尚未验证与明确范围

- 未在用户的安卓品牌手机、厂商文件选择器和常用输入法上验证。
- 最低 SDK 为 26，但没有运行 Android 8.0 真机/模拟器测试；当前运行验证来自 Android 15。
- 当前版本不做系统通知、自动同步、AI 解析、重复日程或跨时区换算。
- 两周试用从用户实际安装开始，尚未完成；记录操作是否顺手、是否减少重复记录、是否容易发现 DDL。

## 重现

正常工程检查使用 `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug`。Release 使用本地签名配置构建，参见 BUILD.md。

模拟器、个人签名材料和工具缓存不在源码包中。测试截图位于 screenshots/，均为虚构样例。
