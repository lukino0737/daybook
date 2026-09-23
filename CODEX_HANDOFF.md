# Daybook 项目交接

更新：2026-09-23。**v0.7阶段1、2已推送并通过CI；阶段3按需读取/回顾已通过本地验收。下一步阶段4：任务局部完成反馈、受影响流程/大字号/进程终止专项和文档收口。周额度不触发停止，仅五小时剩余接近18%才安全收尾。v0.6发布不变。**

## 阶段3最新进度（优先于后面的历史阶段状态）

- AiReads提供查询意图门槛、有范围结构化查询、本地统计、至多20条来源、每轮只查一次、至多4条正文/各4000字符/总输出预算；正文不能指挥第二次扩大查询，伪造ID不能读取，未开放操作拒绝。
- 个人事项可智能按需查询，普通聊天无读库工具；明确禁读或界面切换为仅聊天会清除旧会话、来源和草稿，避免后续携带旧个人数据。显式选便签是用户提供上下文，发送前显示说明。
- 回答显示本地统计和来源跳转；重复提醒复用RepeatRules并注明是计划而非送达。更新日期不能冒充完成时间，缺发生日期的便签不会混入发生日期范围。回顾可生成MEMO草稿，仍需确认保存。
- 69项JVM、构建/Lint通过；API35三项专项通过（AiReadUiTest、AiReviewTest、AiDraftUiTest），含查询→正文→回顾草稿→确认保存。全为假服务/虚构数据，真实DeepSeek效果未验证。证据m3-verified-build.log、m3-device.log。
- 阶段2提交a1de117已推送，CI35816785245成功，证据m2-ci-list.json。阶段3提交/CI以实际Git及后续记录为准；模拟器5554仍运行，所有既有发布不变。

## 阶段2最新进度

- 已实现propose_items结构化工具、单/多条草稿、后续聊天调整、逐项编辑/选择/移除；任务无日期保持无截止，必需日期及无效输出校验。便签可显式选择并预览原文，整理后另存或确认替换；含图便签只允许另存文本，原图文保留。任务拆解沿用普通任务。
- Repository新增批量事务、批次去重、生成ID防覆盖、原便签冲突检查、备份恢复generation检查。模型工具不能直接写数据库，明确确认才调用保存。
- 本地64项JVM、构建/Lint通过（0错误31警告）；API35五项专项通过：3项AiStorageTest、AiDraftUiTest、AiUiTest。固定时钟测试验证跨年上下文，真实模型理解效果仍未验证。日志m2-verified-build.log、m2-device.log，截图m2-drafts.png；m2-final-build.log保留新增测试语法错误记录，修正后复验通过。
- 当前尚无自动业务数据读取、来源跳转或阶段回顾，任务整列变暗尚未修复；下一阶段接受限只读工具。Room6/ZIP JSON6不变，无新增依赖。模拟器5554当前已重新启动并运行，5556未动。
- 新文件AiDraft.kt、AiDraftTest、AiStorageTest、AiDraftUiTest；修改AiSession、AiScreen、EntryRepository及入口。阶段2提交号/CI以实际Git和后续证据为准。

## 2026-09-23实施进度（优先于下方历史计划状态）

- 已完成阶段1：DeepSeek固定HTTPS调用、显式模型/思考选项、Key的Android Keystore加密保存/替换/删除、模型列表连接检查、普通多轮聊天、停止/新对话、失败保留输入及脱敏错误。会话由Application持有，仅进程内存，不写SavedState或业务备份。未新增依赖、未改Room6/ZIP JSON6。
- 已验证：61项JVM（新增4项），失败/错误/跳过0；Debug/AndroidTest构建及Lint通过，Lint0错误31警告（原30项＋AiSettingsStore显式commit的UseKtx建议，保留同步提交结果检查）。API35三项专项通过：独立测试Key加密/重开/删除，假服务聊天返回与清空，真实Activity重建保留输入。日志work/v07/m1-final-build.log、m1-device.log、m1-install.log；普通字号AI页/设置截图m1-chat.png、m1-settings.png。
- 实际DeepSeek付费调用、真实Key、真机、进程被系统杀死后的设备级验收、150%字号尚未验证。JVM新建Session验证空历史，不能替代设备级进程终止验收。没有发布/交付正式包。
- 下一阶段：结构化单/多条草稿、多轮修改、便签整理/任务拆解及事务保存；然后按需只读工具与来源/回顾，最后完成反馈修复及整体专项。当前普通聊天没有读取/写入工具，不能误报完整辅助版已完成。
- 重要新文件：ai/AiProtocol.kt、DeepSeekClient.kt、AiSettingsStore.kt、AiSession.kt；ui/AiScreen.kt。入口DaybookApplication/MainActivity/DaybookScreen；新增AiSessionTest、AiSettingsTest、AiUiTest、AiLifecycleTest。
- Git实施起点main=7ccec1c，阶段1提交498c5ba7ed7109f1ec84e30f4caf6cfe0d82d447已推送main；CI35815229882成功，证据work/v07/m1-ci-final.json、m1-ci-watch.log。其后仅文档收尾提交，最新HEAD用git log读取。专用API35模拟器5554已确认字号1.0并正常关闭，数据保留，日志emulator-stop.log；升级专用5556未启动。只覆盖安装原签名QA包，不卸载/清库，切勿运行旧升级seed。
- 阶段1完成后的历史检查为五小时剩余68%、每周剩余18%；助手错误地因周额度提前停止，此举不是用户要求。用户现明确周额度百分比不影响推进；只在自然节点看五小时窗口，接近18%再安全收尾。规则已同步全局与项目AGENTS.md。恢复按实际五小时额度继续，不沿用旧百分比，不自动兑换重置信用。

## 已批准目标与实施约束

- 用户原拟电脑端/同步、AI及任务完成反馈修复；现明确暂缓电脑端和同步，先实现AI完整辅助版，以后逐步增加Agent权限。不要启动桌面、同步、账号或云端代理项目。
- 用户已有DeepSeek API，主要本人和一位朋友使用。方案为Android客户端直连DeepSeek，应用内配置Key，无需租服务器；不能将Key放入源码、APK、日志或业务备份。朋友使用自己的账号还是由用户承担费用尚未确定，不阻塞可独立配置Key的实现。
- 用户明确批准：自然语言单条/多条录入（任务、日程、记录、便签）、便签整理和待办提取、任务拆解为普通任务、自然语言查询、阶段回顾，以及草稿多轮调整。未来新增自动操作权限仍需另行批准。
- 第一阶段AI生成内容先预览、可编辑、确认后写入；便签整理展示新旧内容，用户选择替换或另存。不要自动修改/删除已有数据。任务没说截止日期时保持无截止日期；模糊或必需日期缺失需让用户补充。批量保存、防重复提交、失败不破坏原数据需专门验收。
- 查询与回顾按用户指定范围在本地筛选相关数据，发送范围应明确；结果可跳转原始内容。数量/完成状态由本地程序计算，不依赖模型编造。录入默认只发送输入及当前日期/时区等必要上下文，不能静默上传全部历史或正文图片。
- 设置提供默认模型选择、AI页面临时切换、独立深度思考选项；不静默换模型。2026-09-22官方文档列出deepseek-flash、deepseek-v4-pro，提供GET /models；Flash作默认候选、简单录入关闭思考的效果应实测后确定，不保证模型名/能力永远不变。文档：https://api-docs.deepseek.com/zh-cn/ 、https://api-docs.deepseek.com/zh-cn/api/list-models/ 、https://api-docs.deepseek.com/zh-cn/guides/thinking_mode/ 。官方说明思考默认开启，应显式传参数。
- 用户已确认普通连续聊天、不长期保存聊天记录；默认不读业务数据，但聊到相关个人事项且需要数据时应智能按需读取。无需每次普通查询确认；查询受范围/预算约束并显示实际来源，语义不清时追问。明确“不要读取”时禁用工具并移除后续请求中的个人数据上下文。只在进程内保留对话，页面往返/Activity重建保留，进程结束后清空，不进入备份。
- 可执行方案及分批验收见docs/V0.7-AI.md。本轮用户明确只制定方案，等其说“开始执行”再实施，不创建额度重置提醒/定时执行，不重复询问已批准范围；若用户提出修改，则先更新方案。
- 已批准计划：1) DeepSeek设置、安全密钥存储及调用层；2) 单/多条录入、草稿调整、便签整理、任务拆解；3) 限定范围查询、来源跳转、阶段回顾；4) 分批针对性验收日期、批量写入、失败重试、内容替换与数据保护，并独立修复任务完成时整列变暗。
- 主要影响新增AI界面、网络调用、Repository查询/受控写入；保留单Android模块、现有分层及普通离线功能。当前Manifest没有INTERNET权限，需要新增。目标不改变既有业务数据库/备份格式；若确需宏观数据结构变化先确认。不将实现授权视作正式发布授权，保留v0.6标签和附件。

### 本轮完成、验证与关键状态

- 已读取本交接、项目AGENTS.md、/Users/lukino/.codex/AGENTS.md，低成本核对Git与现有输出。main、HEAD和本地origin/main均为7ccec1c3630caf3adeba58cfc9f3ad9dee6223ab；v0.6.0仍指向e6818e79dde42395db50df78a578363cd7e98461。开始本轮时工作区干净；发布之后仅文档不同。
- 读取保存的CI成功证据、构建成功与专项/真实覆盖升级/启动通过日志；重新计算本地APK和源码ZIP的SHA256，与发布校验证据一致。本轮没有重新查询远端发布，也没有构建或启动模拟器。
- 任务整列变暗的代码原因明确：DaybookViewModel.toggle走runWrite，全局busy置true并持续到400ms视觉停留结束；DaybookScreen将busy传给所有EntryCard，使Card/Checkbox同时进入禁用样式。已有completing按ID记录，可据此区分行级反馈与全局操作。尚未修改，也未运行复现；不要写成修复通过。
- 初步读取app/build.gradle.kts、MainActivity、DaybookApplication、DaybookViewModel、DaybookScreen、EntryRepository、Entry/Memo及Manifest。现有UUID和updatedAt不等于同步能力；当前不做同步。
- 上述为2026-09-22方案阶段状态；2026-09-23已启动且阶段1完成本地验收，最新结果见本文件开头。完成反馈修复和后续AI阶段仍未实施。
- 在启动大型实施前仅查询一次额度，五小时剩余16%、每周剩余24%。遵循用户低额度先保存交接的要求，未开启高成本开发、构建或QA；没有使用重置信用。额度是当时状态，恢复时不得沿用旧百分比。

### 下一步恢复

1. 用户2026-09-23已授权开始执行，不再等待同一授权。读取本交接、docs/V0.7-AI.md、Git差异，先复用阶段1的有效成果，保留未提交工作。
2. 按已批准的完整辅助版与按需读取聊天方案开始，不必重新批准同一方向。长期聊天存储已明确不做；以后新增Agent权限或其他宏观变更仍先确认。
3. 从现有代码与测试切入，先制定每批的可验证验收条件，然后开始调用层/设置与草稿流程；不得将原计划中五项遗漏为只有快速录入。
4. 无真实Key时可做假服务/固定响应的针对性测试，不要求用户把密钥发在聊天里，不把模拟测试写成真实DeepSeek调用通过。实际调用涉及用户API费用，应在配置和明确操作后进行。
5. 每个完成里程碑更新docs/DEVELOPMENT.md，检查暂存内容并提交推送main；正式发布另行授权。不要重跑v0.6完整QA或修改既有发布。

---

以下为v0.6稳定基线及历史交接证据。

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
