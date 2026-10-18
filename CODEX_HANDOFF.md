# Daybook 项目交接

更新：2026-10-18。本文为新对话恢复入口；历史阶段细节见 docs/DEVELOPMENT.md、docs/TESTING.md 和 Git 历史，不按旧记录中的“待发布/下一步”重新开工。

## 1. 当前项目目标

保持已发布 v0.7.2/code10 的稳定状态。本次用户只要求交接整理，完成后停止；新对话低成本接手并简报，等待用户新的需求或试用反馈。不自动启动功能、重构、QA或发布。

项目目录：`/Users/lukino/Documents/Codex/2026-09-09/ai-coding-ai-coding-ai-coding`。

## 2. 已完成的功能与阶段

- 基础功能：单 Android 应用，本地日历、日程/任务/记录、标签/搜索/回顾、独立便签、外观背景与配色、单次/重复独立提醒、正文图片、完整 ZIP 备份与恢复；新任务默认不设截止日期。
- v0.7.0：可选 DeepSeek 助手，含加密 Key 设置、多轮聊天、单/多条录入草稿、便签整理、任务拆解、按需只读查询与来源、阶段回顾；任务完成反馈仅影响当前项。
- v0.7.1：通知先打开只读详情，再进入编辑；保留草稿、编辑返回、图片查看/缩放和内容失效提示。日历任务只在“近期截止”显示，不进入月历标记或当天列表；删去多余操作说明。
- v0.7.2：提醒列表左滑露出删除按钮并确认；同一编辑框内连续文字—图片—文字；模糊背景居中预览卡片、左上退出/右上编辑；日历当天无可展示内容时隐藏日期标题、节假日说明及空提示卡片，保留月历、近期截止和新增按钮。
- 四项实现提交 e17cc3e；正式发布提交及远程标签 v0.7.2 对应 `1ec2561f5d70c9dcc8cebf17d819e94f3d82ef07`。正式构建、原签名覆盖升级、CI、Release 与三个附件校验均已完成。
- [v0.7.2 正式发布](https://github.com/lukino0737/daybook/releases/tag/v0.7.2)公开、非预发布，发布时已设为 latest；v0.7.1 标签、正文及附件已核验保持不变，其他旧版本未操作。

## 3. 正在进行但未完成的工作

**无待完成的实施、验收或发布步骤。** 本轮交接开始时没有遗留未提交修改。真实 API、真机及 API26 未验证是已知边界，不代表应自动补跑。过去审批服务额度限制导致的文档延误已处理完，不是当前阻塞。

本次仅整理通用文档并提交推送，不修改应用代码或版本，不重建/重传 APK，不改标签和附件。

## 4. 已批准的重要设计决策与授权

- 单 Android 模块；Compose UI / ViewModel / Repository / Room 分层。普通功能离线，可选 AI 由 Android 直连 DeepSeek；无账号、服务器或云代理。
- 日期保持本地日历语义。只有任务可以完成/逾期；未设截止日期的任务保持无截止。数据没有独立完成时间，不用更新时间代替完成时间。
- 单一原生 EditText 与 Compose 整合；图片独占一行，统一光标/选区，继续沿用 BodyBlock。事项显式保存，便签自动保存。正文通过既有草稿状态恢复，避免原生 TextView 保存丢失自定义图片 Span。
- 提醒列表中独立提醒删除整条；任务/日程/便签仅取消附带提醒，原内容保留。仓库写锁内核对记录版本与恢复代次，拒绝旧确认误操作新状态。
- 卡片预览沿用通知入口；普通应用内列表仍直接编辑。背景仅内存模糊快照，长内容卡片内滚动；编辑返回与草稿保护保留。
- Room6 / ZIP JSON6，兼容旧 JSON1–5。保留 schema 与迁移测试；禁止破坏性迁移。恢复先完整校验、预览确认、创建完整恢复快照，再事务替换；失败保留原数据。
- AI 普通聊天不预读业务数据；个人事项按需有限读取并显示来源，禁读后清除旧个人上下文。所有业务写入先经用户预览确认；模型不能直接删除、完成、改期或后台操作。含图便签整理仅另存文字，不上传图片。
- Key 仅设备内加密保存，不进源码、日志或备份；聊天及 AI 草稿只在进程内存。不要索取真实 Key 或自动调用付费生成。
- 新宏观方案须说明推荐与替代方案、优缺点、风险、影响及实施步骤，获用户明确确认后再执行；已批准范围内的微观实现细节自主处理。
- 目标仓库固定为 [lukino0737/daybook](https://github.com/lukino0737/daybook)。用户永久授权正常开发交付所需的源码归档、APK、SHA256、Release/标签上传，不逐版重复确认；不得上传真实个人记录、备份、密钥、签名材料或本地配置，保留旧标签/附件，不强推。授权不意味着自动扩展功能或重复发布。

## 5. 已废弃、取消及暂缓的方案

- 隐私便签、密码/隐藏入口、便签长按拖动排序已取消，不恢复为待办。
- 电脑端、同步、账号/云服务暂缓。
- 多个“文字框 + 图片 + 文字框”的编辑方式已被统一编辑区域替代；图片附件区、提醒连同业务记录删除、仅遮罩不模糊等替代方案未采用。
- 全库上传、长期聊天历史、自主 Agent、语音/图片识别、新任务层级不在当前范围。
- BACKLOG 中历史设想不是实施授权；已完成的自然语言草稿、提醒和图片功能不要再次开工。

## 6. 已知 bug、风险与待解决问题

- 真实 DeepSeek 的生成、日期理解、耗时和费用，以及 v0.7 系列真机效果、API26 实际运行尚未验证。假服务/API35 测试不代表这些已通过。AI 查询/正文有数量上限，截断来源不能当成全量结果。
- 旧日历删除确认框及 ReminderTest 取消通知断言曾在组合测试中偶发失败，未改用例即独立复验通过；根因未确定，不宣称已修复。
- 最近已有 Lint 报告为 0 错误、35 警告；CI action/运行时弃用提示保留，不在本次扩大维护范围。
- 图片与 ZIP 备份没有密码加密。整体恢复旧备份会移除备份中没有的新类型；现有确认提示和完整恢复快照提供保护。
- 图文导入后跨线程操作 View、超长替换被拒绝时误删选区图片已修复并复验。旧测试按整框替换文字、Compose-only 选图模拟未投递、ActivityScenario 强制 RESUMED 的早期失败已由适当测试替代，不列为未修复产品故障。
- v0.6 阶段的鸿蒙6提醒异常用户已反馈解决；不要因历史记录重新开启调查。

## 7. 已验证内容与本次低成本检查

既有验证（本次未重跑）：

- 69 项 JVM，失败/错误/跳过均 0；Debug、AndroidTest 构建通过；Lint 0 错误、35 警告。
- API35 普通字号 30 个不同专项分批通过：图文UI6、原生编辑器7、提醒仓库3、预览4、新交互UI3、真实选图1、图片备份4、通知路由2。150%字号另9项通过；不是一次性完整组合或全仓库 QA。
- 真实 DocumentsUI 前台期间重建 STOPPED 宿主，返回后原光标位置插图；原图删除后副本可读，再次 Activity 重建保留图文。普通/大字号截图已有目视验收。
- 正式 Release/AndroidTest 构建通过；安装基线与已发布 v0.7.1 APK 哈希相同，新旧签名一致。API35 真正 v0.7.1→v0.7.2 覆盖升级 seed/verify 及正式启动各1项通过，任务/日程扩展样例字段及通知渠道保留；未卸载清库。仅代表这些样例和环境。
- 发布提交的 [CI37282754979](https://github.com/lukino0737/daybook/actions/runs/37282754979) 成功。远程 Release、标签提交、latest、三个附件大小/SHA256与旧版不变均已在发布时验证。
- 5554、5556 均有字号恢复1.0及正常关闭记录，数据保留；5556 最后安装正式 v0.7.2，不运行要求旧版本的 seed，不降级或清库。

2026-10-18 本次实际核对：

- 读取当前版本代码、Git、已有CI/构建/覆盖升级/模拟器退出记录、JVM XML报告及Lint XML报告；核对原生编辑器7项、真实选图/通知3项、大字号9项日志和验收文档的分批口径。
- 重新计算三个本地发布附件大小及 SHA256，均匹配 `work/v072-release/published-release.json` 中已保存的远程证据。
- 未重新查询 GitHub Release、运行构建/测试、启动模拟器或改附件；不把历史远程/设备证据表述为新一次验收。

## 8. Git 状态与最近稳定提交

- 分支 `main`。交接开始时 HEAD 和本地 `origin/main` 均为 `12f1eebc30c4282907b1b90ce730d8a20fcfb73d`，工作区干净。
- 最近稳定发布提交 `1ec2561f5d70c9dcc8cebf17d819e94f3d82ef07`（v0.7.2/code10）；到12f1eeb仅三份完成记录变化，应用代码未变。
- 本轮仅整理 CODEX_HANDOFF.md、docs/DEVELOPMENT.md、BACKLOG.md、UX-IMPROVEMENTS.md、TESTING.md；完成后创建文档提交并推送 main，最终提交号与同步状态以 `git log/status` 为准。不提交 work、outputs、真实截图、签名或本机配置，不变更发布标签。
- Git 命令使用 `DEVELOPER_DIR=/Library/Developer/CommandLineTools`，避免默认 Xcode 许可提示；不改系统许可。

## 9. 关键文件、目录和输出

- 约定：项目 AGENTS.md 及 `/Users/lukino/.codex/AGENTS.md`。
- 文档：docs/DEVELOPMENT.md、TESTING.md、USAGE.md、BUILD.md、releases/v0.7.2.md；设计 docs/UX-IMPROVEMENTS.md、V0.7-AI.md。BACKLOG 仅供参考。
- 源码根 `app/src/main/java/dev/lukino/daybook/`：ui/InlineBodyEditText.kt、RichBodyEditor.kt、PreviewBackdrop.kt、NotificationDetail.kt、ReminderListScreen.kt、DaybookScreen.kt、DaybookViewModel.kt；data/EntryRepository.kt、RichBody.kt、DaybookDatabase.kt；ai/、backup/、media/、reminder/。
- 测试：`app/src/test/`、`app/src/androidTest/`；重点 InlineBodyEditorTest、ReminderRemovalTest、UxImprovementsUiTest、RichBodyUiTest、ImagePickerSystemTest、NotificationDetailUiTest、NotificationRoutingTest、UpgradeContinuityTest；历史 Room schema 在 app/schemas/。
- 发布证据 `work/v072-release/`：build.log、release-verification.json、upgrade-run.log、upgrade-seed.log、upgrade-verify.log、release-smoke.log、ci-final.json、published-release.json、publish.log、final-font.log、emulator-stop.log。
- 四项实现证据 `work/ux-improvements/`：editor-boundaries-final.log、targeted2.log、final-normal.log、resume-normal.log、regression.log、real-picker-final.log、final-large.log、ci-final.json；截图 image-previews/、detail-previews/、previews/ux-previews/。部分早期日志含失败/中断，只复用文档明确通过的段落；不要把所有日志都当成完整通过。
- 旧阶段证据 work/v071-release/、work/v071/、work/v07/ 按需复用。work/v04/build.sh 使用已有工具链；work/github_cli.py 复用认证，勿输出凭据。
- 专用AVD：work/avd/（5554）、work/v04/upgrade-avd/（5556）。本机签名材料受忽略规则保护，不输出内容、不提交或重建签名。
- 交付 outputs/daybook-v0.7.2.apk（13014895字节）、daybook-v0.7.2-source.zip、SHA256SUMS-v0.7.2.txt。APK SHA256：`9fc4db4148cd2cf70edaad67a6c497f08f4a0a9e48b1027852c780710af05ed9`。
- work/outputs 不入 Git。换机器若这些证据不存在，如实说明并从已提交文档/发布信息核对，不声称已读缺失文件。

## 10. 下一阶段建议及优先级

1. 接手简报后等待用户需求，保持已发布稳定状态；当前没有需要续跑的开发、测试或发布。
2. 收到问题反馈时，先确认版本、设备环境和最小复现，再进行针对性诊断；不要自动补跑真实API或完整QA。
3. 新功能、架构、桌面/同步或AI权限变化，先提出方案并获用户确认。已取消需求不恢复。

## 11. 新对话恢复时先检查

1. 先读本文件、项目 AGENTS.md、全局 AGENTS.md；以用户最新授权和实际可验证结果为准。
2. 低成本检查 Git 状态、最近提交、HEAD/本地origin/main及相对发布提交1ec2561的差异。新修改先识别来源，不覆盖、不重置。没有本地标签不代表远程未发布。
3. 按需读取 work/v072-release/、work/ux-improvements/ 的已有记录和产物，复用上述验证；不要从头实施四项或重做v0.7.2发布。
4. main 的交接优先于发布源码 ZIP 内的发布前快照；历史文档中的“当时未发布/待完成”不是当前待办。
5. 简报状态后等待用户下一步。额度只在约定自然节点检查五小时窗口，不沿用旧百分比，也不以周额度百分比暂停或缩减任务。
