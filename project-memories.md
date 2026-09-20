# xy_txt_ui 项目记忆导出

> 导出时间：2026-06-23  
> 用途：导入其他编辑器时作为项目上下文参考

---

## 一、项目概述

### 智能地质测井系统核心能力

项目定位：面向地质测井领域的专业Web管理系统，解决多源异构测井数据（TXT/CSV/Excel）的解析、治理、分析与可视化。

核心能力：
- 多格式海量解析（百万行+，BOM清洗+流式入库）
- 标准别名词典映射（统一DEPTH/dept/测深/md等字段）
- 高性能动态宽表+组合索引+TTL自动清理
- 异常地层AI提取+ECharts markArea高亮+厚度测算
- 流式打包导出（SXSSFWorkbook/ZipOutputStream，规避内存假死）

### JWT双令牌自动轮换机制

项目采用JWT双令牌认证机制:
- Access Token：1小时有效期，用于每次API请求的身份校验，存于前端内存或localStorage
- Refresh Token：7天有效期，48字节随机生成并Base64编码，前端存localStorage，后端数据库仅存储其SHA-256哈希值
- 刷新逻辑：Access Token过期返回401后，前端自动调用/auth/refresh；后端验证Refresh Token哈希存在、未撤销、未过期后，签发新Access Token和新Refresh Token，并将旧Refresh Token标记revoked=1、记录replaced_by
- 安全设计：Refresh Token一次性轮换、数据库不存明文、三重校验（存在/未撤销/未过期）、前端Axios拦截器无感续期

---

## 二、技术栈与环境配置

### 前端UI技术栈

项目前端UI基于Element Plus框架构建，关键组件包括el-table、el-drawer、el-select等，所有表格交互需优先使用其原生能力。

### 核心列独立存储与索引配置

项目数据库中，核心测井列（depth, ac, den, gr, sp, rt）以独立字段形式存储，并已建立索引，支持高效范围查询。

### 非核心列JSON聚合存储模式

非核心列（如岩性、井号、CAL、K等）统一存储在log_data_records表的extra_json JSON字段中，该设计是当前数据模型的关键约束。

### log_data_records文本字段配置

数据库log_data_records表共配置10个文本字段（text_col_1~text_col_10），用于存储用户标记的文本列值，支持更丰富的文本分类列映射需求。

### 文本列数量扩展至10列

数据库表log_data_records需扩展文本列至10个，即新增text_col_6至text_col_10字段，以支持更多文本分类列映射。

### 测井数据采样步长

测井数据采样步长为0.125米，分层边界设定需考虑该步长，下一层顶深应等于上一层底深加0.125米以避免重叠。

### 文件上传目录主备配置

文件上传主目录为E:\Others\upload，备用目录为C:\project-upload；当主目录不可用时自动降级使用备用目录。

### 文件上传目录多级备用配置

文件上传目录配置为可扩展的路径列表，按顺序逐个尝试，第一个可用目录即为实际上传路径。配置项为application.yml中的app.upload.dirs，支持任意数量的备用路径。

### TXT文件编码检测规则

文件编码检测采用前8KB字节校验策略：优先校验UTF-8多字节序列合法性，合法则使用UTF-8编码，否则回退使用GBK编码，该逻辑与LogFileParseService保持一致。

### TXT文件编码检测与回退策略

TXT文件读取统一采用自动编码检测策略：先尝试UTF-8解码（验证前8KB是否为合法UTF-8），失败则回退至GBK解码。该策略覆盖预览阶段（extractLinesFromFile）和异步解析阶段（asyncParseAndSaveTxtStream）。

### Excel流式导出分批策略

数据导出采用流式分批写入策略，每批次查询并写入10000行，确保内存占用稳定在约50MB，避免OOM。

### 文本列回填接口性能参数

后端处理文本列回填请求时，需对6991行数据逐行解析JSON并批量UPDATE，平均耗时10~30秒；前端HTTP请求超时设置为5分钟，确保请求不被中断。

---

## 三、架构决策与设计

### JSON扩展字段混合类型筛选的预留字段映射决策

含混合类型（数值/文本）的JSON扩展字段需支持高效筛选时：
1. 预留固定数量数值字段（如extra_col_1~5）和文本字段（如text_col_1~3）
2. 通过log_file_info中的filterColumnMapping配置实现原始列到预留字段的动态映射
3. 查询时优先走预留字段索引，超出预留数则降级为JSON_EXTRACT并前端提示性能风险

### 文本字段存储于extra_json导致筛选性能瓶颈

项目当前数据存储架构中，岩性等非核心文本字段被融合存储在extra_json JSON字段中，导致基于这些字段的筛选查询需使用JSON_EXTRACT函数，无法利用数据库索引，造成全表扫描性能问题。

### JSON_EXTRACT中文路径在JDBC中易编码失败

MySQL JDBC 连接在执行 JSON_EXTRACT 时，若 JSON 路径包含中文（如 '$.岩性'），易因字符编码问题导致 SQL 语法错误（bad SQL grammar）。应避免在 SQL 中直接使用含中文的 JSON 路径，改用 Java 侧解析 JSON 字符串后提取字段。

### 后端基础设施优先于UI开发

项目实施优先级为：先完成数据库字段新增、解析层改造、DISTINCT查询API等后端基础设施，再开发前端筛选UI。因UI依赖后端提供的独立字段和接口能力，否则将导致性能问题或功能不可用。

### 前后端依赖功能实施优先级决策-后端先行

1. 必须后端基础设施（数据库字段、解析逻辑、查询接口）就绪后，再开发前端筛选UI
2. 原因：UI依赖后端提供的DISTINCT查询能力，而该能力又依赖独立字段存储和解析改造，否则50万行数据将卡死

### 高频文本列使用MySQL虚拟生成列+索引

对高频查询的文本列（如岩性），在MySQL中创建虚拟生成列并建立联合索引，使全局筛选和回填操作可走索引，提升查询与更新性能。

### 文本列映射前置到文件解析阶段

文本列映射应在文件解析阶段（LogFileParseService）完成，识别到文本列后直接写入对应text_col_N字段，彻底消除事后回填环节。

### 回填数据性能瓶颈

回填数据性能瓶颈在于后端需对6991行数据逐行JSON解析+批量UPDATE，当前实现效率低、耗时10~30秒。

### 测井数据筛选列排除规则

数据筛选功能需排除以下列：
1. 列名为DEPTH、深度、TVD等索引类列
2. 列名为ID、file_id等内部标识列
3. 所有非空值完全相同的常量列（如全文件统一的井号）

### 前端功能页面级迁移决策

将已有功能从一个页面整体迁移至另一页面时：
1. 彻底删除源页面相关路由、入口、逻辑和UI代码
2. 在目标页面统一实现：数据结构扩展、独立管理函数、分支渲染模板、样式隔离、文档指引同步更新

### 批量扫描跳过交互式预览决策

批量文件扫描流程中不加入数据预览与列映射沙盒交互步骤，采用默认建议映射自动应用；仅对个别映射异常的文件单独重新上传并走完整预览流程。

### 多路径上传目录配置与自动降级决策

1. 在application.yml中以列表形式配置多个上传目录路径（Windows/Linux兼容）
2. 启动时按顺序检查并创建，首个可用路径作为主目录缓存
3. 运行时通过getUploadDir()动态校验，失效则重新扫描列表

### 文本列回填映射机制缺陷与修复

项目文本列回填功能存在映射错乱问题：前端以对象形式发送列启用配置，后端依赖JavaScript对象键迭代顺序分配text_col_N，但该顺序不保证。已修复为前端显式发送列名到text_col_N的映射（如{"井号": "text_col_1", "岩性": "text_col_2"}），后端直接按映射执行回填。

---

## 四、筛选功能

### 筛选功能页面迁移方案

项目筛选功能（含min-max数值筛选与文本列多选）原位于'/files'测井数据看板页面，现已整体迁移至'/extract'异常测段提取页面；同时删除'/files'路由及对应页面。

### 筛选条件区一体化UI重构

将筛选条件区重构为一体化范围切换UI：整合"当前文件/所有文件"切换与筛选组件，支持当前文件模式下按列类型（文本/数值）渲染不同控件，所有文件模式下支持动态增删筛选列、默认加载高频列、显示列存在统计。全栈同步将文本列从5列扩展至10列。

### 筛选范围切换UI重构方案

筛选范围切换UI已重构：将原小radio组升级为大tab式切换栏，包含图标、文件名/数量标识，选中态显示蓝色下划线；该设计使'当前文件'与'所有文件'选项更醒目、更统一。

### 条件筛选按钮组UI优化需求

条件筛选区域的按钮组（条件筛选/清除条件/应用筛选）需用统一div容器包裹以提升视觉一致性与布局控制力。

### 全局筛选模式交互设计方案

'应用于全部文件'模式存在设计缺陷：仅展示当前tab列、文本列误转为数值控件。推荐方案为构建独立筛选配置区，预设DEPTH/GR/SP等核心数值列，支持用户动态增删列（下拉仅显示多数文件存在的列，并标注存在比例），新增列自动适配文本/数值类型控件。

### 文本列筛选功能缺失与增强方案

项目当前筛选功能将所有列统一按定量数据（min-max范围）处理，未区分文本列（如岩性列含'砂岩''泥岩'等分类值）。需增强列类型自动检测能力，并为文本列提供多选下拉筛选UI及后端IN条件支持。

### 全局筛选功能健壮性说明

当前全局筛选功能逻辑正确：筛选条件同步到各文件后，buildFiltersParam会自动跳过文件中不存在的列，确保功能不报错、不异常。

### 异常测段提取页面UI功能增强

异常测段提取页面新增两项UI能力：
1. 多文件筛选区增加'清除筛选'按钮，点击后同步清空全局筛选条件及所有tab的筛选条件
2. 单文件筛选区域的三个操作按钮统一设置为等宽（width: 100%），提升视觉一致性

### extract 页面 UI 与交互增强三阶段实施

对 extract/index.vue 页面进行三阶段 UI 与交互增强：
1. 重构为状态栏+抽屉筛选架构，支持保留率计算与条件计数
2. 增加表头排序、全局/单文件筛选切换及 UI 间距美化
3. 为'保存并回填'按钮添加 loading 状态、空参防护、finally 恢复及后端消息透传

### 筛选功能从测井数据看板迁移至异常测段提取页

将原'测井数据看板'（/files）的筛选功能（min-max 范围筛选 + 文本列多选）整体迁移并整合至'异常测段提取'页面（/extract）。extract 页面支持动态文本列配置（最多 5 个）、实时回填、多选过滤；UI 提供可折叠配置面板与清晰提示；全站导航与帮助文档同步更新。

---

## 五、地质分层功能

### 地质分层配置功能设计方案

地质分层配置功能采用4种方式：
1. 手动输入（P0，已实现）
2. Excel导入（P2，适合首次批量配置）
3. 跨文件复制（P1，推荐优先实现，同区块多井层名相似，复制后微调深度效率最高）
4. 测井曲线自动识别建议（P3，远期辅助功能）

核心原则是层名复用优先、深度按井微调。

### 分层功能完整说明

分层功能是为测井文件配置地层分段，每层包含层位名称、顶深、底深三个核心字段。支持4种配置方式：手动输入、Excel导入（智能识别表头关键词）、跨文件复制、清空。分层后导出时自动追加'层位'列，异常提取时按深度自动跨层段切割。边界匹配采用左闭右开规则[topDepth, bottomDepth)。

### 分层深度精度规范

分层配置中顶深和底深字段的输入与显示精度为小数点后3位，例如100.125。

### 分层边界匹配逻辑修正

分层边界匹配逻辑由左闭右闭区间`[topDepth, bottomDepth]`改为左闭右开区间`[topDepth, bottomDepth)`，确保深度值等于bottomDepth时归属下一层，消除边界点重复匹配歧义。

### Excel分层导入表头智能识别与多井预筛选规范

Excel导入分层数据时，后端需智能识别表头关键词：包含'层'或'layer'的列为层名列，包含'顶'或'top'的列为顶深列，包含'底'或'bottom'的列为底深列，包含'备注'或'remark'的列为备注列，包含'井'或'well'的列为井名列；多井情况下需根据当前文件名自动匹配井名，匹配失败时返回可用井名列表供前端选择。

### CSV/TXT分层导入技术方案

分层导入功能扩展支持CSV和TXT格式：
- CSV：使用EasyExcel CSV模式解析，自动检测UTF-8/GBK编码
- TXT：手动解析流程为检测编码→读取全文→按优先级识别分隔符（tab > comma）→拆分为行列
- 所有格式均复用现有表头关键词匹配规则（井/层/顶/底/备注）

### 清空分层功能入口

分层功能新增'清空分层'操作入口，位于配置界面操作栏最右侧，采用danger样式+Delete图标，点击后弹出确认框并调用deleteFileLayers API清空当前文件所有分层配置。

### 分层配置功能增强需求

分层配置功能支持两种新增方式：
- P1 跨文件复制：将当前文件的分层配置一键复制到多个目标文件
- P2 Excel导入：上传Excel文件，按固定列顺序解析并覆盖

### 分层配置功能实施优先级决策-P1+P2优先

实施P1（跨文件复制分层）和P2（Excel导入分层），暂不实施P3。

### 测井系统地质分层功能实现

成功交付地质分层功能：独立well_layer表存储分层配置；FileController提供/{id}/layers全量CRUD；导出Excel/CSV自动根据depth匹配层名并追加'层位'列；异常提取时检测分层边界并自动切分连续段，每段标注对应层名。

---

## 六、仪表盘与工作台

### 仪表盘工作台模式（方案A）落地实现

将仪表盘首页从静态系统概览升级为动态工作台模式（方案A），聚焦待处理文件状态，支持解析中/失败/成功三级排序与快捷操作。后端通过log_file_info表精准统计，前端按status优先级排序、动态生成pendingHint、格式化相对时间；UI完全去icon化。

### 仪表盘左侧改造为工作台模式

将仪表盘左侧区域从静态系统说明页改造为动态'我的工作台'，展示用户最近5个文件的真实状态（解析中/失败/成功），支持状态排序、快捷跳转与智能提示。

### 仪表盘看板数据源规范

仪表盘看板数据应基于真实业务表聚合计算：
- '已加载测井文件'取自log_file_info表中status != 2的记录数
- '累计解析测井长'取自log_file_info表中status != 2记录的total_rows字段总和
- '导出Excel报表'可继续使用操作日志计数

---

## 七、系统设置页

### 系统设置页功能精简

系统设置页已移除「显示与外观」tab，当前仅保留「个人信息」「安全隐私」「操作日志」3个tab。

### 系统设置页新增操作日志与存储用量功能

- 操作日志：提供`GET /sys/log/all`接口分页获取用户全部操作记录，前端以新tab表格展示，按模块打彩色Tag
- 存储用量：提供`GET /sys/log/storage`接口获取文件数、解析总行数、脏数据行数、操作日志总数，前端以2×2卡片弹窗展示

### 运行日志功能端到端实现

为系统添加内存环形运行日志查看功能：后端使用 LinkedList 实现 500 条环形缓冲，前端采用深色终端风格 UI + 3 秒自动轮询 + 级别着色 + 平滑滚动。

### 后端核心模块运行日志结构化增强

为6大核心模块（认证、看板、用户、字段映射、日志解析、报表导出）注入结构化日志：全部使用@Slf4j，日志以方括号标签区分功能域，包含关键参数，覆盖INFO/DEBUG/WARN/ERROR四级。

### 系统设置页运行日志功能需求

项目需在系统设置页新增'运行日志'功能模块，用于实时查看后端终端日志（内存环形缓冲，最多500条），支持按级别过滤、自动轮询（3秒）、手动刷新与清空。

### 运行日志面板滚动行为

运行日志终端面板设置了固定高度420px和垂直滚动overflow-y: auto，当日志条目超出可视区域时自动显示滚动条。

### 单机版清除缓存功能替代定时任务

将原定时自动清理逻辑替换为单机版用户手动触发的「清除缓存」功能，覆盖5类数据：已删除文件元数据、明细数据、脏数据、30天前操作日志、已撤销/过期刷新令牌。

---

## 八、使用说明体系

### 使用说明页面内容规范

使用说明页面内容需覆盖10个主题：新手入门（首次上传、批量扫描）、核心功能（数据源管理、看板筛选、异常提取与可视化、数据导出、字典映射）、常见问题（解析乱码、导出性能、系统设置），每条说明包含完整步骤、注意事项和跳转链接。

### 使用说明文档扩展内容

项目使用说明文档已扩展：
- 新增'地质分层配置'条目
- 新增'运行日志查看'条目
- 更新'系统设置与账号管理'条目

### 项目介绍文档更新规范

项目介绍.md文档需同步最新功能变更，行文风格要求直白务实，避免'专业''高效'等AI常用修饰词。

### 演示应使用最新版本

当前演示使用最新版本，相比旧版具有UI优化、功能增强和问题修复，旧版存在已知bug，不建议用于正式演示。

---

## 九、文件解析与编码鲁棒性

### 表头智能探测与防御机制

表头识别采用鲁棒探测策略：扫描前100行，按字典匹配得分选取最优表头行；若全为数字则终止探测；无匹配时兜底用Col1/Col2命名；自动处理重复列名（加_1后缀）；预览阶段支持人工干预修正。

### 统一TXT文件预览与异步解析的编码检测逻辑

完成编码检测全链路统一：预览阶段（extractLinesFromFile）和异步解析阶段（asyncParseAndSaveTxtStream）均改用detectFileEncoding(file)动态识别编码。

### EasyExcel 3.3.3 不支持 .csv() 方法

EasyExcel 3.3.3 版本不支持 `.csv()` 方法调用；CSV 文件应与 TXT 文件共用同一手动解析逻辑，通过 `parseTxtToRows` 自动检测分隔符。

### EasyExcel 3.3.3 中止读取异常类路径变更

EasyExcel 3.3.3 版本中，用于提前终止读取的异常类已从 `com.alibaba.excel.event.AnalysisStopException` 迁移至 `com.alibaba.excel.exception.ExcelAnalysisStopException`。

### Java文件上传目录fallback失效与mkdir可靠性问题

Java中File.mkdir()返回boolean仅表示调用是否成功，不代表目录一定创建成功；且UPLOAD_DIR变量未随fallback逻辑动态更新，导致启用备用目录后仍写入原主目录而报错。

---

## 十、前端UI交互与视觉规范

### UI设计一致性规范

UI组件设计需参考异常测段提取页面的样式风格，保持按钮尺寸、颜色、间距等视觉一致性。

### UI元素间距设计规范

UI元素间距遵循以下规范：按钮组gap为12px；抽屉section-body内边距为20px；筛选项之间添加底部分隔线且垂直间距为20px；范围输入框内间距为10px。

### 按钮交互前端实践规范

前端按钮交互需遵循：
1. 点击后立即显示loading状态并变更文案
2. 成功提示必须使用后端返回的实际消息而非硬编码文案
3. 执行前增加空值校验
4. 使用finally块确保无论成功或失败都恢复按钮初始状态

### 表头排序技术实现规范

表头排序功能需使用Element Plus el-table组件原生sortable属性实现，禁止自行封装排序逻辑。

### Element Plus el-button等宽显示实践

在Element Plus项目中，为使el-button在flex容器内等宽显示，应使用`:deep(.el-button)`穿透样式，并设置`display: block`和`margin-left: 0`。

### 分层Dialog UI样式规范

分层配置Dialog的UI样式规范：操作按钮统一使用`size="default"`，底部确认按钮使用`size="large"`，Dialog整体宽度设为720px，表格去除边框、采用浅色表头。

### 仪表盘图标使用规范

仪表盘页面的图标需保持视觉体系统一，避免风格突兀；在功能明确、空间紧张或已有足够语义表达的区域，可省略图标以减少视觉干扰。

### UI图标精简与体系一致性决策

1. 所有新增icon必须与现有icon属于同一体系
2. 已存在大量icon的区域，优先不添加新icon
3. 仅在必要功能提示处保留icon（如警告、错误等语义强的场景）

### 图标复用规范

页面中使用的图标必须复用已有图标组件，禁止新增图标或替换为不同语义的图标。

### UI重构功能零变更规范

UI布局调整类修改必须严格保持原有功能逻辑不变，禁止新增、删除或变更任何业务行为、API调用、数据流和状态管理逻辑。

### 文件名防重命名规则

文件上传时采用'UUID_原始文件名'格式生成存储文件名，防止同名文件覆盖冲突。

---

## 十一、开发规范与最佳实践

### 路径配置化规范

项目中禁止路径硬编码，所有文件系统路径应通过application.yml等配置文件注入，便于跨环境部署和维护。

### 实体新增字段需同步更新init.sql建表语句

Java实体类新增字段后，数据库初始化SQL（init.sql）必须同步添加对应列定义，否则save/update操作会因字段不存在而报错。

### init.sql升级脚本位置规范

init.sql文件末尾需包含文本列功能所需的ALTER TABLE升级语句，供已有数据库迁移使用。

### Vue文件修改前备份规范

对Vue单文件组件进行修改前，需先复制原文件并添加.bak后缀作为备份。

### data目录git忽略与清理配置

项目需在.gitignore中添加'data/'规则，使整个data目录及其子文件不再被git追踪；同时执行'git rm --cached -r data/'命令将已提交的data目录从git索引中移除。

---

## 十二、经验教训

### PowerShell中检查文件存在应使用Test-Path而非ls -la

PowerShell中`ls -la`命令不可用（非标准cmdlet），应使用`Get-ChildItem`或`Test-Path`检查文件存在性。

---

## 十三、关键文件索引

### 筛选UI重构与文本列扩展相关文件
- /front-end/src/views/extract/index.vue#L115-L180, L260-L290, L341-L380
- /database/init.sql#L195-L215
- /back-end/src/main/java/com/xy/welllog/entity/LogDataRecord.java#L1040-L1050
- /back-end/src/main/java/com/xy/welllog/controller/LogDataController.java#L215-L219

### 筛选功能迁移相关文件及关键行号
- /front-end/src/views/extract/index.vue#L405-L409, L794-L862, L650-L710, L1333-L1400
- /front-end/src/views/instructions/index.vue#L209-L213, L234-L247, L301, L647
- /front-end/src/views/dashboard/index.vue#L117, L143-L144

### 地质分层功能相关文件
- /database/init.sql#L166-L177
- /back-end/src/main/java/com/xy/welllog/entity/WellLayer.java
- /back-end/src/main/java/com/xy/welllog/mapper/WellLayerMapper.java
- /back-end/src/main/java/com/xy/welllog/service/WellLayerService.java
- /back-end/src/main/java/com/xy/welllog/service/impl/WellLayerServiceImpl.java
- /back-end/src/main/java/com/xy/welllog/controller/FileController.java#L564-L617
- /back-end/src/main/java/com/xy/welllog/controller/LogDataController.java#L284-L340
- /front-end/src/api/file.js#L110-L134
- /front-end/src/views/datasource/index.vue#L185-L298
- /front-end/src/views/extract/index.vue#L584-L758

### 分层管理功能增强相关文件
- /front-end/src/views/datasource/index.vue#L277-L310, L556-L590, L842-L920
- /back-end/src/main/java/com/xy/welllog/controller/FileController.java#L673-L720, L860-L957

### 工作台模式改造相关文件
- /back-end/src/main/java/com/xy/welllog/mapper/SysOperationLogMapper.java#L1-L28
- /back-end/src/main/java/com/xy/welllog/controller/DashboardController.java#L1-L54
- /front-end/src/api/dashboard.js#L1-L9
- /front-end/src/views/dashboard/index.vue

### 运行日志功能相关文件
- /back-end/src/main/java/com/xy/welllog/config/InMemoryLogAppender.java
- /back-end/src/main/java/com/xy/welllog/controller/SysOperationLogController.java#L201-L217
- /front-end/src/api/log.js#L30-L35
- /front-end/src/views/settings/index.vue#L465-L517

### 运行日志增强涉及的核心后端文件
- /back-end/src/main/java/com/xy/welllog/controller/AuthController.java
- /back-end/src/main/java/com/xy/welllog/controller/DashboardController.java
- /back-end/src/main/java/com/xy/welllog/controller/UserController.java
- /back-end/src/main/java/com/xy/welllog/controller/SysColumnMappingController.java
- /back-end/src/main/java/com/xy/welllog/service/LogFileParseService.java
- /back-end/src/main/java/com/xy/welllog/controller/LogDataController.java

### 操作日志与存储用量功能相关文件
- /back-end/src/main/java/com/xy/welllog/controller/SysOperationLogController.java#L32-L158
- /front-end/src/api/log.js#L40-L54
- /front-end/src/views/settings/index.vue#L195-L391

### 清除缓存功能相关文件
- /back-end/src/main/java/com/xy/welllog/WellLogApplication.java#L1-L17
- /back-end/src/main/java/com/xy/welllog/mapper/LogFileInfoMapper.java#L1-L20
- /back-end/src/main/java/com/xy/welllog/mapper/LogDataRecordMapper.java#L1-L20
- /back-end/src/main/java/com/xy/welllog/controller/SysOperationLogController.java#L154-L200
- /front-end/src/api/log.js#L20-L25
- /front-end/src/views/settings/index.vue#L85-L92, L387-L422

### 分层配置增强功能相关文件
- /back-end/src/main/java/com/xy/welllog/controller/FileController.java#L564-L620
- /front-end/src/api/file.js#L108-L134
- /front-end/src/views/datasource/index.vue#L240-L310, L530-L590
