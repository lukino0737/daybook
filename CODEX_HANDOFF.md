# Daybook 项目交接

更新：2026-09-28。**四项体验调整已实现并完成专项验收，正在提交推送main及核对CI。已发布版本仍为v0.7.1/code9；新代码尚未发布APK，旧标签和附件不变。**

## 最新实施状态（2026-09-28，恢复时优先）

### 当前目标与授权

用户已明确要求实施四项并「确认采用」推荐方案，范围见docs/UX-IMPROVEMENTS.md。实现与本地专项验收已完成，收尾为提交推送main并核对CI；不自动启动额外功能。永久上传授权继续有效，但本轮没有启动新版本发布。

### 已完成

- 提醒列表左滑露出删除按钮后确认；独立提醒删除整条，任务/日程/便签只取消附带提醒、原内容保留。数据库写锁内验证修改版本与恢复代次。
- 单一连续图文编辑区域：原生EditText与Compose整合，光标、选区跨图文；图独占一行并可继续文字。原BodyBlock、Room6、ZIP JSON6与历史schema不变，无新依赖。
- 模糊背景居中预览卡片，左上退出、右上编辑；短内容自然收缩、长内容卡片内滚动，编辑返回与草稿保护保留。背景仅内存快照。
- 日历当天没有可显示内容时隐藏日期标题、节假日说明和空提示卡片；月历、近期截止及新增按钮保留。
- 导入后跨线程操作控件、超长替换拒绝时误删选区图片已复现并修复；选图位置可恢复，正文由SavedStateHandle恢复，不依赖会丢失自定义图片Span的原生TextView保存。

### 已验证结果

69项JVM通过（失败/错误/跳过均0），Debug/AndroidTest构建通过，Lint0错误35警告。API35普通字号30个不同专项分批通过；150%字号9项通过并恢复字号1.0。已查看普通/大字号连续图文、卡片预览、空态和删除确认截图。真实DocumentsUI选图期间重建STOPPED宿主，返回仍在原光标插图；删除原图副本可读、再次Activity重建与通知路由通过。详细分批口径、失败日志及修复见docs/TESTING.md；不宣称一次性完整QA。

### 尚未完成、边界与环境

- 剩余：提交推送main、核对CI并记录结果。专用模拟器已确认字号1.0并正常关闭，证据final-font.log、emulator-stop.log。完成后的Git状态以实际git log为准。
- 本轮没有生成/发布新正式APK；app版本号仍为0.7.1/code9。现有v0.7.1正式附件不包含这四项新变化；旧发布提交dc29229不变。
- 真实DeepSeek、真机和API26实际运行未验证；Lint及历史偶发测试问题未扩大维护范围。
- 仅专用API35模拟器5554同签名覆盖QA包，原数据保留、无卸载清库；5556未操作。用户原始标注截图未保存或提交。

### 关键文件与证据

- 主代码：ui/InlineBodyEditText.kt、RichBodyEditor.kt、PreviewBackdrop.kt、NotificationDetail.kt、ReminderListScreen.kt、DaybookScreen.kt、DaybookViewModel.kt，data/EntryRepository.kt（路径前缀app/src/main/java/dev/lukino/daybook/）。
- 新专项：InlineBodyEditorTest、ReminderRemovalTest、UxImprovementsUiTest；更新RichBodyUiTest、ImagePickerSystemTest、NotificationDetailUiTest。文档：docs/UX-IMPROVEMENTS.md、USAGE.md、TESTING.md、DEVELOPMENT.md、BACKLOG.md。
- 本地work/ux-improvements/：verified-build.log、final-build2.log、real-picker-final-build.log；editor-boundaries-final.log七项、targeted2.log提醒三项所在批次、final-normal.log图文UI六项段、resume-normal.log十四项、regression.log备份四项段、real-picker-final.log三项、final-large.log九项；image-previews/、detail-previews/、previews/ux-previews/已查看。
- 曾失败的Compose-only选图结果模拟与ActivityScenario强制RESUMED尝试已由真实系统STOPPED宿主重建测试替代，不作为未修复产品故障。临时诊断日志已从产品代码移除。

### Git与恢复建议

实施基线main=4259b71，本轮代码/通用文档待提交；不提交work、构建产物、个人截图或本机配置。恢复时先看最新提交及CI记录，复用上述已验证结果，勿重复实施四项或重新发布v0.7.1。后续新宏观方案仍须先获用户确认。

---

以下是v0.7.1发布后的历史摘要，关于「没有待完成工作」等描述仅代表四项调整获批之前。

### 1. 当前目标

保持Daybook已发布稳定状态，保存可验证的交接信息。本次只更新交接和开发记录，完成后停止；没有已批准但尚未启动的新开发阶段。

### 2. 已完成的功能与阶段

- 基础：单Android应用，本地日历、日程/任务/记录、标签与回顾、独立便签、外观背景、单次/重复独立提醒、正文内图片、完整ZIP备份/恢复；新任务默认无截止日期。
- v0.7.0：可选DeepSeek助手，包括设置与加密Key、多轮聊天、可编辑单/多条录入草稿、便签整理/待办提取/任务拆解、按需只读查询与来源、阶段回顾；任务完成反馈仅影响当前项。
- v0.7.1：点通知先只读详情，小号「编辑」按钮进入原编辑器，保存或退出后返回详情；图文查看/缩放、内容更新/删除提示、现有草稿保护。只改通知入口，应用内普通列表仍直接编辑，便签仍自动保存。
- 两项先前「仅记录」需求已获批准并完成：任务在日历页只出现在近期截止，不进入月历标记或当天列表；移除插图大小/压缩说明和提醒暂停/日历开关说明，功能仍保留。这两项不再是待办。
- v0.7.1发布、原签名覆盖升级、CI和三个远程附件校验全部完成；永久上传授权文档也已随7b51f43提交推送。

### 3. 正在进行但未完成的工作

**无。** 本轮开始时无未提交修改。真实API/真机未验证是已知验证边界，不代表应自动补跑。此前审批服务额度限制只延误了文档提交，随后已完成，不是当前阻塞。

### 4. 已批准设计与授权

- 单模块，Compose UI / ViewModel / Repository / Room分层；普通功能离线，可选AI由Android直连DeepSeek，无账号、服务器、云代理或新依赖。
- 日期保留本地日历语义；只有任务可以完成/逾期，无明确截止日的任务保持无截止。数据没有独立完成时间，不把更新时间冒充完成时间。
- AI普通聊天不预读业务数据；个人事项问题仅按需有限读取并显示来源，禁读清除旧个人上下文。所有业务写入必须由用户预览确认；模型不能直接删除、完成、改期或后台操作。含图便签整理仅另存文字。
- Key在设备内加密保存，不进源码、日志或备份；聊天与AI草稿仅进程内存，进程结束清空。真实API由用户设备内配置，不索取Key或自动发起付费调用。
- 保持Room6/ZIP JSON6；禁止破坏性迁移。恢复先完整校验、预览确认、创建恢复快照，再事务替换，失败保留原数据。
- 新宏观方案先说明推荐/替代方案、优缺点与影响并获用户确认；已批准范围内细节自主处理。
- 用户永久授权向 https://github.com/lukino0737/daybook 上传Daybook源码归档、APK、SHA256清单及对应Release/标签；无需逐版重复确认。不得上传个人记录、备份、密钥、签名材料、本地配置；保留旧标签与附件，不强推。这不等于授权自动扩展新功能或重做已完成发布。

### 5. 已取消、暂缓及不采用的方案

隐私便签、密码/隐藏入口、便签长按拖动排序已取消；电脑端、同步、账号/云服务器暂缓。正文采用内嵌图片，旧附件区方案不采用。全库上传、长期聊天历史、自主Agent、语音/图片识别、新任务层级均不在当前范围。历史backlog不是实施授权，已完成的自然语言草稿和图片条目不要再次开工。

### 6. 已知问题与边界

- 真实DeepSeek生成效果、日期理解、耗时与费用，以及v0.7系列真机效果尚未验证；假服务测试不能代表线上模型或所有机型。读取数量/正文有限，来源截断不能当成全量结果。
- 旧日历删除确认框与ReminderTest取消通知断言分别有过组合运行偶发失败，独立复验通过，根因未确定；未宣称已修复。
- Lint31警告、CI action/运行时弃用提示保留，本轮不扩大维护范围。
- 图片和ZIP备份没有密码加密；整体恢复旧备份会移除其中没有的新类型，依靠既有确认提示与完整恢复快照保护。
- v0.7.1首轮专项的重复文本匹配失败属于测试选择器范围问题，已修正并复验，勿列为未修复产品故障。

### 7. 已有验证及本轮检查

- 已有最终构建成功；69项JVM，失败/错误/跳过均0；Lint0错误31警告。本轮读取了现有报告，没有重新运行。
- API35最终普通字号9项、150%字号4项通过；覆盖只读图文、编辑保存/取消返回、删除失效、三类草稿保护、日历与任务展示、Activity启动/重建/onNewIntent及实际通知PendingIntent后台跳转。已有普通/大字号截图目视验收通过，字号恢复1.0。不是物理设备通知栏手指点击验收。
- 已发布v0.7.0 APK哈希验证一致后，同签名覆盖正式v0.7.1；扩展任务/日程样例字段、通知渠道保留，seed/verify/Smoke各一项通过。未卸载/清库，仅代表API35及这些样例。
- 发布提交dc29229的CI35945001582成功。已保存发布证据确认v0.7.1为公开正式版本、当时为latest，v0.7.0原标签/附件不变。
- 本轮重新计算三个本地附件大小/SHA256，与已保存远程证据完全一致；APK为12998507字节，SHA256为64c554d90adaabfd1d63cc997e75c138329f61fdbd2ac62726d3e4d73b09029f。
- 本轮未查询远程发布、构建、运行测试、启动模拟器或更新附件；不把本地历史证据写成新一次远程/设备验收。

### 8. Git与关键状态

- 分支main；本轮开始HEAD和本地origin/main均为7b51f4381993c12606f6c9e280dd4fbf460517c8，工作区干净。
- 最近稳定发布提交：dc2922972dfdd1c6a788baa54493d49e321f97bb（v0.7.1/code9）。其后到7b51f43只有六份文档变化，业务代码未变。
- 本次只改CODEX_HANDOFF.md与docs/DEVELOPMENT.md，文档提交完成后的最新HEAD以git log为准，不改发布标签/附件。两个专用模拟器已有字号1.0及正常关闭日志，数据保留；5556最后安装正式v0.7.1，不运行旧seed或降级清库。

### 9. 关键文件与输出

- 文档：AGENTS.md、docs/DEVELOPMENT.md、docs/TESTING.md、docs/USAGE.md、docs/BUILD.md、docs/releases/v0.7.1.md；AI方案docs/V0.7-AI.md；docs/BACKLOG.md仅作历史参考。
- 源码根app/src/main/java/dev/lukino/daybook/：ui/NotificationDetail.kt、DaybookScreen.kt、DaybookViewModel.kt、RichBodyEditor.kt、StandaloneReminderEditor.kt；ai/、data/、backup/、reminder/相关代码沿用已验证成果。
- 专项：app/src/androidTest/java/dev/lukino/daybook/下NotificationDetailUiTest、NotificationRoutingTest、StandaloneUiTest、TaskListsUiTest、UpgradeContinuityTest；单元测试app/src/test/，Room历史schema在app/schemas/。
- 本机证据work/v071/（build-final.log、candidate-normal.log、candidate-large.log、previews-final/）；work/v071-release/（ci-final.json、publish.log、published-release.json、resume-latest.json、release-verification.json、upgrade-run.log）。旧AI证据work/v07/可复用。
- 交付outputs/daybook-v0.7.1.apk、daybook-v0.7.1-source.zip、SHA256SUMS-v0.7.1.txt；正式发布页 https://github.com/lukino0737/daybook/releases/tag/v0.7.1 。work/outputs被Git忽略，不提交产物或真实用户截图。

### 10. 下一阶段建议（不等于启动授权）

1. 接手简报后等待用户新需求或试用反馈，保持现有已发布状态。
2. 如有反馈，先收集版本、环境和最小复现，再做针对性诊断修复；不自动补跑真实API或完整QA。
3. 新功能、桌面/同步或AI操作权限变化先讨论方案并获批准，不从历史路线自动续做。

### 11. 新对话恢复顺序

1. 读取本文件最上方摘要、项目AGENTS.md及/Users/lukino/.codex/AGENTS.md。
2. 低成本检查git status、最近提交、HEAD与本地origin/main及相对dc29229差异；新改动先识别来源，不覆盖。Git命令加DEVELOPER_DIR=/Library/Developer/CommandLineTools。
3. 按需读取work/v071-release/及work/v071/已有证据，复用已验证成果。缺少本机证据时如实说明，用已提交验收文档核对，不声称已读取缺失文件。
4. main交接优先于发布源码ZIP内旧交接；后者对应发布时刻，可能写着待发布。本地缺少标签也不等于未发布。
5. 简报后等待用户需求；不重建、完整QA、重新上传或修改旧标签附件。无需恢复任何发布步骤。额度仅在规定自然节点查看五小时窗口，不沿用旧百分比或以周额度暂停。

---

以下是2026-09-24及更早的详细交接记录，供按需查证；恢复动作以上方摘要为准。

## v0.7.1 当前接续状态（优先）

- 永久交付授权：项目目标仓库固定为 [`github.com/lukino0737/daybook`](https://github.com/lukino0737/daybook)。用户已永久授权 Codex 将本项目源码归档、APK、SHA256 校验清单及对应 Release/标签上传到该仓库；后续版本不因同类交付重复询问。授权不包含真实个人记录、备份、密钥、签名材料、本地配置或其他敏感文件，旧版本标签与附件必须保留。

- 已实现：通知先只读详情，右上角小号编辑按钮进入原编辑器、退出后回详情；复用图片查看与缩放，数据库变化同步，缺失内容提示。事项/便签/独立提醒草稿仍受保护。
- 已实现：日历页任务仅在近期截止显示，不进入月历标记/日期列表；删除插图大小压缩说明和暂停/日历开关说明。任务/提醒实际能力不变。
- 版本0.7.1/code9，Room6/ZIP JSON6未变，无新依赖。普通应用内列表仍直接编辑，当前范围只调整通知入口。
- 已验证：work/v071/build-final.log构建Debug/Release/AndroidTest、69项JVM通过；Lint0错误31警告。candidate-normal.log九项、candidate-large.log四项通过；普通/150%字号三种详情截图已检查。首轮测试选择器重复匹配失败已修正并复验，不是产品故障。字号已恢复1.0。
- 已完成：原签名v0.7.0→v0.7.1覆盖升级和正式启动已通过；提交dc29229已推送，CI35945001582成功；v0.7.1正式发布与远程附件校验通过。发布阶段开始时五小时额度26%。
- 5554专项完毕，QA包同签名覆盖安装保留数据。5556已从已发布v0.7.0覆盖到正式v0.7.1，扩展样例字段和通知渠道保留，未降级或清库。
- v0.7.1提交dc2922972dfdd1c6a788baa54493d49e321f97bb已推送main，CI35945001582成功；正式 Release 为 https://github.com/lukino0737/daybook/releases/tag/v0.7.1，三个附件大小/SHA256 已核对，v0.7.0 标签、Release 与原附件保持不变。重要文件NotificationDetail.kt、DaybookViewModel.kt、DaybookScreen.kt、RichBodyEditor.kt；专项NotificationDetailUiTest/NotificationRoutingTest与StandaloneUiTest。
- 两个专用模拟器已正常关闭并保留数据，字号1.0。真实DeepSeek及v0.7真机效果仍未验证。隐私便签/拖动排序取消，电脑端/同步暂缓。
- 最新恢复检查：用户要求继续发布后，只读查询GitHub latest并重新计算三份本地产物摘要，确认v0.7.1已公开且三个附件完整一致（work/v071-release/resume-latest.json）。没有待续的实施、构建或发布步骤，不重建、不重传附件。
- 上次被审批服务额度硬限制阻断的是永久授权文档的提交/推送，不是安装包发布。本次仅补齐授权、README下载入口、开发/验收与交接文档的提交推送；发布标签仍指向dc29229，最新文档提交以git log为准。
- 当前目标已完成后等待用户试用反馈；恢复时优先读取本节，再核对Git与work/v071-release/已有证据，勿按下方旧版本“当前/下一步”重复开工。

---

以下为v0.7.0及更早版本的历史记录；其中“当前”“latest”“下一步”均以当时状态为准，现状以上方v0.7.1接续状态为准。

## 本次交接检查与当前目标

- 用户要求结束本对话，仅整理交接。目标是保存可复用的稳定状态，交接后停止开发；下一对话先简报接手结果，等待新需求。
- **正在进行但未完成的功能、测试、发布工作：无。** 真实API效果/真机尚未验证是已公开的验证边界，不代表应自动补跑测试或再次发布。
- 本轮实际分支main；开始交接时HEAD和本地origin/main均为10319cfc69f9f28356c99cb583f697e27651ba5a，工作区干净、无遗留未提交修改。最近稳定发布提交为64d9adba371813bf9c6180b6edfae287b9615234；它与10319cf之间只改了四份文档，业务代码未变。
- 本轮读取已有CI、构建、正式升级/启动日志、69项JVM报告（失败/错误/跳过均0）及Lint报告（0错误31警告）；核对版本0.7.0/code8、Room6与可选AI网络权限。重新计算本地三个发布附件SHA256，与已保存远程发布证据逐项一致。
- 没有重新查询远程发布、构建、启动模拟器或运行测试；本轮检查的是现有文件和已保存证据，不冒充新的远程/设备验收。本轮只更新交接与开发记录，文档提交后最新HEAD以git log为准。

## 当前发布进度（优先读取）

- 实施基线main=fb0cb19；应用代码5512b59的69项JVM、分阶段专项与CI成功，直接复用。
- 已完成0.7.0/code8正式构建、发布说明和使用文档；签名与v0.6相同，真实已发布v0.6→v0.7同签名覆盖升级及正式启动通过。扩展样例任务/日程字段与通知渠道保留，未卸载清库。证据work/v07-release/build.log、release-verification.json、upgrade-run.log。
- 发布提交及标签v0.7.0指向64d9adba371813bf9c6180b6edfae287b9615234；[CI35819574227](https://github.com/lukino0737/daybook/actions/runs/35819574227)成功。[正式Release](https://github.com/lukino0737/daybook/releases/tag/v0.7.0)公开、非预发布、已设为latest。
- 已上传outputs/daybook-v0.7.0.apk、daybook-v0.7.0-source.zip、SHA256SUMS-v0.7.0.txt；远程三附件大小/SHA256与本地完全一致。APK为12982127字节，SHA256为49b0b2d1f963ea307a6c4148f616b7bb3871e9f44e124a3f4720ef8979180146。源码来自发布提交，不含本机配置、签名材料或产物。
- 2026-09-23远程操作曾因系统硬额度限制未执行；2026-09-24恢复后只完成远程发布与核验，没有重建或重复升级。原v0.6标签、Release正文及附件ID/大小/哈希均核验不变。证据work/v07-release/ci-final.json、published-release.json、v06-before.json。
- 真实DeepSeek与v0.7真机效果仍未验证，发布说明明确说明；用户已知该边界并批准发布，不要求Key或付费调用。
- 专用5556已在升级后确认字号1.0并正常关闭，数据保留（emulator-stop.log）；5554此前已关闭。后续仅完成记录的文档提交，最新HEAD用git log核对；不要重跑旧seed或降级清库。没有待续发布步骤。

## 当前目标与授权

- 用户已说“开始执行”，授权实施docs/V0.7-AI.md的完整辅助版及单条任务完成反馈修复，无需重复确认。Android直连DeepSeek；电脑端、同步、账号、云代理暂缓。未来扩大Agent自动操作权限仍需另行批准。
- 普通聊天、单/多条录入、便签整理及待办提取、任务拆解为普通任务、按需查询、阶段回顾、多轮草稿调整。默认不预读业务数据，个人事项问题可按需读取有限相关记录并显示来源；明确禁读会清除旧个人上下文。所有业务写入经用户预览确认。
- 用户已有API Key，主要本人和朋友用。在App配置，不要求将真实Key发到聊天里；不读取本机其他凭据。真实API调用产生用户费用，不把假服务验收说成线上验证。
- 不长期保存聊天：仅Application进程内存，页面返回/Activity重建保留，进程结束清空。确认保存的事项照常持久化。用户现已明确授权发布v0.7.0；按上方最新发布交接执行，不改v0.6标签和附件。

## 已完成工作

- 稳定基础功能：本地日历、日程/任务/记录、标签与回顾、独立便签、外观与背景、单次/重复独立提醒、正文内插图、完整ZIP备份与恢复；新任务默认无截止日期。v0.6成果保留，历史详情见docs/TESTING.md与DEVELOPMENT.md。
- 设置、加密Key存储/替换/删除、模型选择/深度思考、连接检查、普通多轮聊天、停止/新对话与脱敏失败提示。只访问固定DeepSeek HTTPS，限制重定向、时长及响应大小，无新增依赖。
- 可编辑、勾选、移除的单/多条草稿，任务默认无截止日期；批量事务、防重复、便签替换冲突及备份恢复失效检查。便签整理可另存或确认替换文字，含图便签只能另存文本，原图保留。
- 智能查询意图门槛、结构化只读工具、本地日期/数量统计、来源跳转与回顾草稿。每轮至多一次查询、20条来源、4条受限正文；伪造来源、检索正文要求扩大查询、未开放操作均不获额外权限。普通聊天不提供读库工具。
- 完成任务改为按ID反馈；其他卡片不因本次完成被禁用。连点去重，仓库写锁内比较最新任务，避免覆盖已经编辑/删除的数据，保留提醒送达字段。
- Room6/ZIP JSON6未变，无数据库迁移或新外部依赖。Manifest新增INTERNET仅供可选AI功能；普通记录继续离线使用。
- 全局及项目AGENTS、开发记录已纠正额度规则：以五小时窗口为准；周额度18%不代表应停止。此前因周额度提前停止是助手误判，不是用户要求；不要沿用旧百分比，不自动兑换重置信用。

## 当前已批准的重要设计决策

- 单Android模块，Compose UI / ViewModel / Repository / Room分层。普通记录离线，AI由客户端直连DeepSeek，无账号、服务器或通用Agent框架；新增网络能力属于已批准的v0.7范围，旧v0.6“不联网”仅为历史描述。
- 日期保留本地日历语义，只有任务可以完成/逾期。新任务无明确日期就无截止；日程/记录仍需发生日期，不把更新时间当作完成时间。
- 模型由用户选择，深度思考独立切换，不静默换模型。Key在本机Keystore支持的存储中加密，禁止进入源码、日志、聊天、业务备份和仓库。
- 普通聊天不预先读取数据，个人事项问题可限定范围只读；读取显示口径及来源。禁读会移除旧个人上下文；聊天/AI草稿仅进程内存，页面往返与Activity重建保留，进程结束清空。
- 所有写入先预览、编辑、选择、确认；批量事务、防重复与冲突检查由本地执行。模型不能直接删除、改期、完成或后台执行。含图便签整理只可另存文字，保留原图文。
- 禁止破坏性迁移。备份恢复先完整校验、预览确认、保存完整快照，再事务替换；失败保留原数据。保持Room6/ZIP JSON6，schema与迁移测试保留。
- 宏观方向、新重要功能/权限、数据格式或架构变化，必须先说明推荐与替代方案、利弊及影响并获用户确认；批准范围内细节自主处理。完成里程碑按项目约定更新开发记录、提交推送main，不强推；新发布须获对应授权。

## 已取消、暂缓与不采用的方案

- 隐私便签、密码/隐藏入口方案取消；便签长按拖动排序取消，均不恢复为待办。
- 电脑端（先Mac后Windows的讨论）、实时同步、云服务器/账号暂缓，当前未授权实施。不要因为“AI已完成”自动推进这些方向。
- 不采用每次发送全库、不保存长期聊天历史；本版不支持语音/图片识别、自主Agent操作或新的任务层级。未来扩大权限另议。
- 图片采用正文内插图，旧附件区方案未采用；独立提醒不扩展图片。农历、单次例外等旧backlog不等于实施授权。
- docs/BACKLOG.md含历史条目：其中自然语言可编辑草稿、正文插图已实现，应以本交接及当前代码为准；日期理解的真实模型效果尚未验证，不得将历史条目整体视为待做清单。

## 已验证结果与证据

- 阶段1：61项JVM、构建/Lint；API35三项Key/聊天/Activity重建专项。阶段2：64项JVM、五项草稿与存储专项。阶段3：69项JVM、三项只读/回顾/草稿专项。均通过；具体分批日志在work/v07/m1-*、m2-*、m3-*。
- 阶段4当前最终应用构建m4-final-build.log成功；69项JVM，失败/错误/跳过0；Lint0错误31警告。版本0.7.0-dev/code8；QA包沿用原签名覆盖安装，没有卸载/清库。
- m4-normal.log七项通过：TaskCompletionTest两项、TaskListsUiTest两项、AiReadUiTest、AiDraftUiTest、AiSettingsTest。验证局部完成/连点、过时任务拒写及旧任务默认行为。
- m4-process-seed.log、m4-process-restart.log分别通过，外部force-stop之间验证进程结束后聊天不恢复。为测试增加专用参数保护，防止普通整类测试共享进程产生干扰；最终m4-process-seed-final.log、m4-process-restart-final.log各一项通过。
- m4-large.log两项通过；150%字号来源/草稿截图已查看，来源内容、编辑控件可读。保存按钮补充完整滚动截图m4-drafts-large-final.png，m4-large-final.log复验通过。字号已恢复1.0。
- m4-regression.log五项中四项通过：手动录入/重建、备份快照、图片事务失败保护、图文保存失败恢复。旧ReminderTest取消通知断言在组合运行失败一次，未改用例独立复验m4-reminder-recheck.log通过；根因未查明，不写成首次全过或已修复。
- 所有AI自动测试使用假服务/虚构内容；未使用真实DeepSeek Key、未做付费生成或v0.7真机测试。正式v0.6→v0.7升级验收已在发布阶段完成，见顶部；没有重新执行v0.6完整QA。

## Git与当前关键状态

- main阶段1：498c5ba，CI35815229882成功；阶段2：a1de117，CI35816785245成功；阶段3：cc1ef97，CI35817572296成功。证据m1-ci-final.json、m2-ci-list.json、m3-ci-final.json。
- 阶段4提交5512b59已推送main：任务按行反馈、进程/界面专项、版本code8和使用/开发/验收文档。[CI35818542035](https://github.com/lukino0737/daybook/actions/runs/35818542035)成功，证据m4-ci-final.json。后续仅完成记录文档提交，最新HEAD用git log核对。
- 专用模拟器5554和5556均已确认字号1.0并正常关闭，数据保留；5556现为正式v0.7.0。切勿运行旧版本升级seed或降级清库。正式v0.6.0仍为e6818e7/code7，当前最新发布为v0.7.0/code8。

## 尚未完成、已知限制与恢复建议

1. 实现、本地专项、CI、正式发布与远程校验均完成。恢复时先读本交接、AGENTS、Git与已有证据，复用成果；等待用户新的需求或试用反馈，不重复构建、完整QA或发布。
2. 后续用户在AI设置填Key，先检查模型列表，再实际试用普通聊天、跨月日期、多条草稿/修改、便签整理、按需查询/禁读、回顾。默认候选deepseek-flash与deepseek-v4-pro基于2026-09-22官方文档；连接检查不等于生成效果验证，不静默换模型。
3. 意图门槛是保守规则，含糊表达可能需要明确查询对象/日期；模型理解、响应耗时与费用尚待真实API验证。查询数量/正文有上限，截断和日期口径必须看提示。数据无独立完成时间，不将更新时间冒充完成时间。
4. Lint31警告：原30项＋密钥同步commit结果检查的UseKtx建议；CI action弃用提示不在本版维护范围。提醒组合用例一次失败见上，保留证据。
5. 正式发布及必要覆盖升级检查已随发布请求获授权；真实API与真机验证边界仍如实记录。旧隐私便签与拖动排序仍取消，勿从历史backlog自动开工。

补充已知边界：旧日历删除确认框用例曾在组合测试中偶发失败，独立复验通过，根因未确定；与上方ReminderTest取消通知断言分别记录，不视为已修复。正文图片和ZIP备份没有密码加密；整体恢复旧备份会移除其中没有的类型，依赖已实现的提示和恢复快照。签名覆盖升级仅验证特定版本、API35模拟器与样例，不能保证所有机型、所有历史数据都已验证。

## 下一阶段建议与优先级（建议不等于授权）

1. 首先保持v0.7.0已发布状态，完成接手简报后等待用户需求；没有必须继续的开发/发布任务。
2. 用户若提供AI或界面反馈，先收集最小复现和版本环境，针对性诊断/修复。真实API试用在用户设备内配置Key，区分线上模型结果与假服务测试；不自动获取Key或发起付费请求。
3. 新功能、Agent权限、桌面/同步等先讨论方案与替代选择，待用户批准再实施；不因backlog或历史“下一步”自动开工。

## 新对话恢复顺序

1. 先读CODEX_HANDOFF.md、项目AGENTS.md、/Users/lukino/.codex/AGENTS.md；当前项目为/Users/lukino/Documents/Codex/2026-09-09/ai-coding-ai-coding-ai-coding。
2. 低成本核对git status、最近提交、HEAD与本地origin/main，以及相对64d9adb的差异。新出现的改动先识别来源，勿重置/覆盖；标签的远程核验已保存在published-release.json，本地没有标签不等于未发布。
3. 按需查看work/v07-release/ci-final.json、published-release.json、release-verification.json与upgrade-run.log及outputs三个v0.7产物；已有work/v07测试证据直接复用。不启动构建/模拟器、完整QA、重传附件或重新发布。
4. main的本交接优先于发布源码ZIP中的旧交接快照；后者产生于发布提交，可能仍写“下一步发布”。work/outputs不入Git，换机器缺失时如实说明，使用已提交验收记录与发布页核对，不伪称读取过本机证据。
5. 检查后简报状态并等待用户下一步需求。不要沿用旧额度百分比；仅在自然节点看五小时窗口，周额度不触发停止。

## 重要文件与环境

- 方案docs/V0.7-AI.md；发布说明docs/releases/v0.7.0.md；用法docs/USAGE.md；验收docs/TESTING.md；开发记录docs/DEVELOPMENT.md；历史需求docs/BACKLOG.md。
- 新增ai/AiProtocol.kt、DeepSeekClient.kt、AiSettingsStore.kt、AiSession.kt、AiDraft.kt、AiReads.kt；UI为ui/AiScreen.kt，入口DaybookApplication/MainActivity/DaybookScreen。上述源码根为app/src/main/java/dev/lukino/daybook/。
- 受控保存与完成：data/EntryRepository.kt、DaybookDatabase.kt、ui/DaybookViewModel.kt。对应Ai*及TaskCompletionTest测试在app/src/test和app/src/androidTest；Room schema不变。
- 本机证据work/v07/（忽略，不提交）；Git命令加DEVELOPER_DIR=/Library/Developer/CommandLineTools。构建复用work/v04/build.sh，GitHub复用work/github_cli.py，不输出凭据。
- 专用AVD在work/avd，5554；升级AVD在work/v04/upgrade-avd，5556。work/startup-qa/install_test.py仅内部QA同签覆盖安装；正式包outputs/daybook-v0.7.0.apk，源码与清单同目录。既有v0.6产物保留不变。

---

以下为v0.6稳定基线及历史交接证据。

**归档区：下方“当前/最新/下一阶段”均指v0.6当时状态，尤其latest、无网络、AVD版本等不可当作现状；恢复工作只遵循上方v0.7交接。保留历史仅供查证，不重新执行其任务。**

## 本次交接检查与未完成工作

- 本轮仅整理交接，没有新功能、重构、构建、模拟器启动、完整QA或重新发布。
- 实际分支main；交接开始时HEAD与本地origin/main均为4413e9ce75cd9653f440f548d00ce06e2f79681a，工作区干净。该提交是发布完成记录；最近稳定业务提交为e6818e79dde42395db50df78a578363cd7e98461（v0.6.0）。二者之间仅三份文档不同。
- 已读取现有构建、三项任务/提醒专项、正式升级/启动日志及JVM/Lint报告；本地三个发布附件重新计算的大小和SHA256均匹配work/v06-release/published-release.json中的远程发布证据。本轮未重新请求远程发布接口，不把保存的历史证据描述为新一次远程检查。
- **正在进行但未完成的功能、测试、发布：无。** 开始交接时没有遗留未提交修改；首次交接整理已于4e55c7f提交推送；随后按用户实测反馈追加文档更正，最新提交号以git log读取。

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

## 最新用户实测反馈

- 鸿蒙6真机检验通过；原提醒异常已不再发生。用户要求不再反复提及，不再列入新对话启动指令或后续待办。来源：2026-09-22用户明确反馈，本轮未自行运行真机测试。

## 已知问题、风险与待解决事项

- 旧日历删除确认框用例在组合测试中偶发失败，未修改同一用例的独立复验及最后日历专项通过；根因未确定。本轮没有复现或修复，不标记为已解决。
- Lint有30项警告、0错误；CI仍提示部分action/Node版本弃用。均未在本轮扩大维护范围。
- 正文图片及备份没有密码加密；旧版不能读取新ZIP图文备份。恢复不含新增类型的旧备份可能移除当前对应数据，需保留确认提示和完整回滚快照。
- 已在API35模拟器验证真实v0.5→v0.6原签名覆盖升级，不等于所有历史版本或所有真机均验证通过。

## 下一阶段建议与优先级（建议不等于实施授权）

1. 最高优先：保持当前已发布稳定状态。新对话完成低成本接手检查后，简报并等待用户新的具体需求；当前无需继续开发或补跑验收。
2. 如用户反馈可复现问题，先保存最小复现与环境信息，再决定针对性修复；不自动开启长期监测。
3. 如用户提出新功能，再结合docs/BACKLOG.md讨论范围和替代方案；不重启已取消的隐私/排序，不擅自定下一版本或发布计划。

## 当前目标与授权

- 用户取消隐私便签；此前仅提出方案，没有实施加密、密码或隐藏入口。便签拖动排序也已取消，不再作为待完成批次。
- 用户授权把新任务默认改为「不设截止日期」，完成后发布v0.6.0安装包。该修改、针对性验收、真实旧版覆盖升级、提交推送、CI、发布及远程附件校验均已完成。
- 不自动启动backlog。农历、重复任务、多时段、提前提醒、单次例外等未纳入。
- 用户已关闭此前两项真机/提醒待办；不重新开启调查或验收。

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
