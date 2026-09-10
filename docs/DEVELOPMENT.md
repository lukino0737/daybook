# 开发日志

## 初始化 · 2026-09-10

- 目的：先固定产品行为和开发约定，避免代码生成偏离范围。
- 已完成：README、第一阶段计划、AGENTS.md 与忽略规则。
- 检查：工作目录原本没有代码或 Git 仓库；已有 Git 作者配置。Android SDK 尚待安装。
- 限制：此提交尚无 APK；GitHub 浏览器连接超时，正在配置命令行托管方式。
- 学习入口：docs/PLAN.md。先掌握安排、任务和记录之间的行为差异。

## 里程碑① · 工程骨架与安装验证

- 已完成：公开仓库、Gradle Wrapper（含 SHA-256 校验）、固定依赖、原生应用骨架、图标与基础主题、CI 构建检查。
- 验证：assembleDebug 成功；Android 15 / API 35 ARM64 专用模拟器安装并通过 1 项启动测试。
- 工具链：JDK 21、Gradle 8.13、AGP 8.13.2、SDK 36、Build Tools 35.0.0。模拟器使用 macOS 虚拟化。
- 限制：尚未在用户真机安装；本里程碑界面仅用于验证启动，不包含日历功能。
- 学习入口：MainActivity → setContent；settings/build.gradle.kts → Gradle 如何组织和编译应用。

## 里程碑② · 记录闭环

- 已完成：安排/任务/记录统一表单、Room 数据库与 schema、当天列表、修改、完成/恢复、删除与短时撤销、SavedStateHandle 草稿。
- 验证：APK 构建成功；7 项日期/字段规则单元测试通过；4 项模拟器测试通过（启动、数据库重开与删除恢复、写入失败回滚、界面草稿重建与编辑保留 ID）。
- 失败验证：快照回调抛错、SQLite 触发器强制插入失败，原数据均未丢失。
- 限制：月历视图与备份入口在后续里程碑；用户真机仍待验证。
- 学习入口：EntryEditor → DaybookViewModel.save → EntryRepository.save → Room。注意保存成功后才关闭表单。
