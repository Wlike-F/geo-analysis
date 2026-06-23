-- ======================================================================================
-- well_log_db 全新建库脚本
--   - 适用：MySQL（开发环境）/ MariaDB（打包 exe 便携版）全新建库，drop 后重建
--   - 特性：仅使用 CREATE TABLE IF NOT EXISTS，两套库通用，幂等可重复执行
--   - 老库就地升级请改用同目录 mariadb_upgrade.sql（仅 MariaDB，依赖 IF NOT EXISTS 扩展）
-- ======================================================================================

-- 创建测井数据分析数据库
CREATE DATABASE IF NOT EXISTS `well_log_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `well_log_db`;

-- 1. 用户表
CREATE TABLE `sys_user` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码 (加密)',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名/网名',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '绑定邮箱',
  `introduction` VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `role` VARCHAR(20) DEFAULT 'user' COMMENT '角色: admin管理员, user普通用户',
  `sys_settings` TEXT COMMENT '系统偏好设置(JSON)',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态 (1正常, 0禁用)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统账号表';

-- 预置一名管理员 (密码: 123456 的 MD5+盐值)
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role`) VALUES ('admin', '72a1d3bbe9947ba73758e663c0d82143', '系统管理员', 'admin');

-- 2. 系统操作日志表
CREATE TABLE `sys_operation_log` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT(20) DEFAULT NULL COMMENT '操作用户ID',
  `module` VARCHAR(50) NOT NULL COMMENT '操作模块 (如: 用户登录, 文件上传, 文件扫描, 报表导出)',
  `description` VARCHAR(255) NOT NULL COMMENT '操作详情',
  `file_count` INT(11) DEFAULT 0 COMMENT '涉及文件数',
  `line_count` BIGINT(20) DEFAULT 0 COMMENT '涉及解析行数',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志与统计表';

-- 插入一些初始模拟数据
INSERT INTO `sys_operation_log` (`module`, `description`, `file_count`, `line_count`, `create_time`) VALUES 
('系统初始化', '初始化核心分析系统', 10, 50000, DATE_SUB(NOW(), INTERVAL 2 DAY)),
('文件加载', '批量载入 5 个测井历史档案', 5, 25000, DATE_SUB(NOW(), INTERVAL 1 DAY)),
('报表导出', '导出了门限过滤结果 (Excel)', 1, 1500, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
('用户登录', '管理员登录系统', 0, 0, DATE_SUB(NOW(), INTERVAL 10 MINUTE));

-- [架构升级] 核心数据建模设计 - 海量数据宽表存储机制

-- 1. 文件主表 (File Metadata)
CREATE TABLE IF NOT EXISTS `log_file_info` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '上传者ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件原始名称',
  `columns_json` TEXT COMMENT '动态列名配置，JSON数组格式保存',
  `text_columns_json` TEXT COMMENT '文本列配置，JSON对象{原始列名:text_col_N}',
  `total_rows` INT DEFAULT 0 COMMENT '总解析行数',
  `status` TINYINT DEFAULT 1 COMMENT '1:正常 2:逻辑删除，待定时任务物理清除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测井文件上传主表';

-- 2. 测井数据大宽表 (Log Data Records)
CREATE TABLE IF NOT EXISTS `log_data_records` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `file_id` BIGINT NOT NULL COMMENT '关联log_file_info表的ID',
  `depth` DECIMAL(10,4) COMMENT '深度（核心比对条件）',
  -- 静态核心列 --
  `ac` DECIMAL(10,4),
  `den` DECIMAL(10,4),
  `gr` DECIMAL(10,4),
  `sp` DECIMAL(10,4),
  `rt` DECIMAL(10,4),
  -- 动态拓展列，满足任意多少列都展示的需求 --
  `col_01` DECIMAL(10,4),
  `col_02` DECIMAL(10,4),
  `col_03` DECIMAL(10,4),
  `col_04` DECIMAL(10,4),
  `col_05` DECIMAL(10,4),
  `extra_json` JSON COMMENT '超出预留列的罕见极长列可使用JSON扩展',
  -- 文本预留列（用于岩性、地层名等分类文本字段的筛选） --
  `text_col_1` VARCHAR(200),
  `text_col_2` VARCHAR(200),
  `text_col_3` VARCHAR(200),
  `text_col_4` VARCHAR(200),
  `text_col_5` VARCHAR(200),
  `text_col_6` VARCHAR(200),
  `text_col_7` VARCHAR(200),
  `text_col_8` VARCHAR(200),
  `text_col_9` VARCHAR(200),
  `text_col_10` VARCHAR(200),
  
  -- 利用复合索引加速查询 --
  INDEX `idx_file_depth` (`file_id`, `depth`),
  INDEX `idx_file_main` (`file_id`, `ac`, `den`, `gr`),
  INDEX `idx_file_text1` (`file_id`, `text_col_1`),
  INDEX `idx_file_text2` (`file_id`, `text_col_2`),
  INDEX `idx_file_text3` (`file_id`, `text_col_3`),
  INDEX `idx_file_text4` (`file_id`, `text_col_4`),
  INDEX `idx_file_text5` (`file_id`, `text_col_5`),
  INDEX `idx_file_text6` (`file_id`, `text_col_6`),
  INDEX `idx_file_text7` (`file_id`, `text_col_7`),
  INDEX `idx_file_text8` (`file_id`, `text_col_8`),
  INDEX `idx_file_text9` (`file_id`, `text_col_9`),
  INDEX `idx_file_text10` (`file_id`, `text_col_10`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测井明细大宽表';


-- 3. 系统使用说明表
CREATE TABLE IF NOT EXISTS sys_instruction (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  title VARCHAR(100) NOT NULL COMMENT '折叠面板标题',
  icon VARCHAR(50) COMMENT '图标名称',
  content TEXT COMMENT '具体使用说明内容支持HTML',
  sort_order INT DEFAULT 0 COMMENT '排序号',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统使用说明表';

INSERT INTO sys_instruction (title, icon, content, sort_order) VALUES
('新手入门：首次上传测井文件', 'Reading', '<p>本系统支持 TXT、CSV、Excel 格式的测井文件上传与智能解析。首次使用建议按以下步骤操作：</p><p>1. 登录系统后，点击左侧菜单进入"数据源管理"页面。</p><p>2. 点击"上传文件"按钮，选择本地的 TXT、CSV 或 Excel 测井文件（单次上传上限 50MB）。</p><p>3. 系统自动进入预览阶段，识别表头并生成列名映射建议，请检查每一列的映射是否正确。</p><p>4. 确认映射无误后点击"确认上传"，系统将在后台异步解析，状态会显示为"解析中"。</p><p>5. 解析完成后状态变为"成功"，此时可进入"测井数据看板"查看解析结果。</p><p>注意：TXT 文件默认使用 GBK 编码读取，CSV 文件自动检测编码，Excel 文件无需关注编码问题。</p>', 1),

('服务器目录批量扫描', 'PriceTag', '<p>如果测井文件已存放在服务器本地目录中，可以使用批量扫描功能一次性导入多个文件：</p><p>1. 在"数据源管理"页面找到"服务器扫描"区域。</p><p>2. 输入服务器上存放测井文件的绝对路径（如 E:\\data\\welllog）。</p><p>3. 系统自动递归扫描目录下所有 .txt、.csv、.xls、.xlsx 文件。</p><p>4. 已存在同名记录的文件会自动跳过，避免重复导入。</p><p>5. 扫描完成后所有新文件将自动提交后台异步解析。</p><p>注意：请确保路径正确且后端进程对该目录有读取权限。扫描大量文件时请耐心等待，所有任务会在后台并行处理。</p>', 2),

('测井数据看板与筛选', 'Lock', '<p>数据看板是浏览和筛选测井数据的核心页面：</p><p>1. 进入"测井数据看板"，系统自动加载已解析的文件列表。</p><p>2. 点击某个文件名即可展开该文件的测井数据表格，表格列根据文件实际列名动态生成。</p><p>3. 每一列都支持设置最小值和最大值进行范围筛选，输入后点击"提取数据"查看筛选结果。</p><p>4. 支持切换每页显示数量（100/200/500/1000 条），翻页浏览全部数据。</p><p>5. 筛选完成后可以导出为 Excel 或批量 ZIP 格式。</p><p>注意：筛选条件为空时将显示全部数据。如果文件刚上传还在解析中，请稍后再刷新查看。</p>', 3),

('异常测段提取', 'Bell', '<p>异常测段提取功能用于自动识别满足条件的连续深度区间：</p><p>1. 先在"异常测段提取"页面选择需要分析的文件（支持同时选择多个文件）。</p><p>2. 为每个文件设置筛选条件：选择目标列（如 GR），设定最小值和最大值范围。</p><p>3. 多文件模式下可以使用统一的全局筛选条件一键应用到所有已选文件。</p><p>4. 点击"提取数据"后系统自动分析，识别满足条件的连续测段。</p><p>5. 结果展示每个异常段的顶界深度、底界深度、厚度等关键信息。</p><p>6. 支持将提取结果导出为 Excel 或 ZIP 格式。</p><p>注意：提取功能只做数据分析展示，不会修改原始解析数据。</p>', 4),

('异常测段可视化（ECharts 曲线图）', 'Lightbulb', '<p>可视化页面提供多通道测井曲线的交汇图与异常段高亮标注：</p><p>1. 进入"异常测段可视化"页面，从下拉框选择目标测井文件。</p><p>2. 选择"曲线显示通道"（最多 3 条），如 AC、GR、DEN。</p><p>3. 选择"统计通道"，系统将计算异常段内这些通道的极值和均值。</p><p>4. 配置"异常识别条件"：选择通道、运算符（大于/小于）和阈值。支持添加多个条件。</p><p>5. 设置"最短连续测点数"以过滤零散噪点，然后点击"执行分析与渲染"。</p><p>6. 图表区域会显示多道曲线，异常段以红色半透明区域高亮标注。</p><p>7. 可以通过底部滑块或鼠标滚轮缩放深度区间，点击"查看明细"查看异常段列表。</p><p>注意：可视化结果仅用于分析展示与导出，不会修改原始数据。大数据量文件后端会自动降采样以保证渲染速度。</p>', 5),

('数据导出指南', 'DocumentCopy', '<p>系统支持多种导出方式，满足不同场景需求：</p><p>1. 单文件导出：在看板中对某个文件完成筛选后，点击"导出 Excel"按钮，下载当前筛选结果的 .xlsx 文件。</p><p>2. 批量 Excel 导出：选择多个文件后点击"批量导出"，系统生成一个包含多个 Sheet 的 Excel 文件。</p><p>3. 批量 ZIP 导出：选择多个文件后点击"批量 ZIP"，系统将每个文件的筛选结果分别生成 Excel 并打包为 ZIP 下载。</p><p>4. 异常段 CSV 导出：在可视化页面点击"导出 CSV"，下载异常测段的明细数据。</p><p>5. 图表图片导出：在可视化页面点击"导出高清图片"，下载 PNG 格式的曲线图。</p><p>注意：Excel 单个工作表上限为 1,048,576 行。导出上限为 100 万行，超大数据建议分批筛选导出或使用 ZIP 格式。</p>', 6),

('字典映射参数配置', 'PriceTag', '<p>字典映射用于将不同来源测井文件中的列名归一化为系统标准列名：</p><p>1. 进入"字典映射参数"页面，查看当前所有映射规则。</p><p>2. 每条规则包含：标准列名（如 DEPTH）、中文含义（如 测量深度）、别名列表（如 dept,tvd,深度,测深）。</p><p>3. 上传文件时系统会自动匹配别名，将识别到的列名映射为标准列名。</p><p>4. 可以新增自定义映射规则，例如添加 SONIC 列映射到 AC。</p><p>5. 可以编辑现有规则的别名列表，增加更多别名以提高匹配率。</p><p>注意：核心列（如 DEPTH）受保护无法删除。修改映射规则后，只对新上传的文件生效，已解析的文件不受影响。</p>', 7),

('文件解析失败或数据乱码', 'Bell', '<p>遇到解析问题时，请按以下步骤排查：</p><p>1. 检查文件状态：在数据源管理页面查看文件的解析状态是否为"失败"（红色标记）。</p><p>2. 编码问题：TXT 文件默认使用 GBK 编码读取。如果文件实际是 UTF-8 编码，中文内容可能出现乱码。建议将文件另存为 GBK 编码后重新上传。</p><p>3. CSV 编码：CSV 文件支持自动检测 UTF-8 和 GBK 编码，如果仍有乱码请手动转换编码后重试。</p><p>4. 脏数据处理：解析过程中无法识别的行会自动存入脏数据表，不影响正常数据的入库。可在"解析报告"中查看脏数据行数。</p><p>5. 列数不匹配：如果数据行的列数与表头差异过大（多于 3 列或少于一半），该行会被标记为脏数据。</p><p>注意：单个文件上传上限为 50MB。如果需要处理更大的文件，建议拆分为多个小文件后分批上传。</p>', 8),

('导出失败与性能问题', 'DocumentCopy', '<p>导出相关问题排查：</p><p>1. Excel 导出失败：检查导出数据量是否接近 104 万行上限。如超出请缩小筛选范围后重试，或改用 ZIP 格式分文件导出。</p><p>2. 导出超时：大数据量导出可能需要较长时间，请耐心等待浏览器下载完成，不要关闭页面。</p><p>3. 大文件上传慢：50 万行以上的文件解析需要 1~3 分钟，解析期间文件状态显示为"解析中"，完成后自动变为"成功"。</p><p>4. 看板加载慢：系统采用分页加载机制，默认每页 100 条，可在页面底部切换每页数量。</p><p>5. 可视化页面卡顿：系统对大数据量文件自动降采样至 5000 点以保证渲染速度，图表视觉几乎不受影响。</p><p>注意：如果多次尝试仍然失败，请保留浏览器控制台的错误信息并联系管理员排查。</p>', 9),

('系统设置与账号管理', 'Lock', '<p>系统设置页面提供个人信息维护和安全管理功能：</p><p>1. 个人信息：可以修改真实姓名、绑定邮箱、个人简介和头像（支持外链图片 URL）。</p><p>2. 修改密码：建议定期更换密码。修改成功后系统自动退出，需要使用新密码重新登录。</p><p>3. 存储用量：点击"查看用量"可查看当前账号已上传文件数、解析总行数、脏数据行数和操作日志条数。</p><p>4. 操作日志：记录所有文件上传、解析、导出、删除等操作历史，支持分页查看和按模块分类筛选。</p><p>5. 登录日志：在"安全隐私"中可以查看最近的登录记录。</p><p>注意：每个用户只能查看和操作自己上传的文件，不同用户之间的数据完全隔离。</p>', 10),

('地质分层配置', 'PriceTag', '<p>为测井文件配置地层分段（层名、顶深、底深）：</p><p>1. 在"数据源管理"页面找到目标文件，点击操作列的"分层"按钮打开分层配置对话框。</p><p>2. 手动输入：在可编辑表格中逐行填写层位名称、顶深（m）、底深（m）和备注，点击"保存分层配置"。</p><p>3. 从文件导入：点击"从文件导入"上传 Excel/CSV/TXT 文件，系统自动识别表头中的"层"、"顶"、"底"关键词定位列。</p><p>4. 如果导入文件包含多口井的数据，系统会弹出井名选择对话框，选择当前文件对应的井名后再导入。</p><p>5. 跨文件复制：配置好一口井后点击"复制到其他文件"，勾选同区块其他井，层名结构会被复制。</p><p>6. 配置完成后，导出 Excel 时会自动追加"层位"列；异常测段提取时如果测段跨越分层边界会自动切割为多段。</p><p>注意：分层边界采用左闭右开规则：顶深 ≤ depth < 底深。建议下一层的顶深 = 上一层的底深，避免重叠或空隙。</p>', 11),

('运行日志查看', 'Bell', '<p>在系统设置中实时查看后端服务的运行日志：</p><p>1. 进入"系统设置"页面，点击左侧导航的"运行日志"选项卡。</p><p>2. 日志面板以深色终端风格显示后端实时日志，包括时间戳、日志级别、来源类名和消息内容。</p><p>3. 通过顶部下拉框可按级别筛选：ALL、INFO、WARN、ERROR。</p><p>4. 日志每 3 秒自动刷新，新日志自动滚动到底部。</p><p>5. 点击"刷新日志"可立即手动拉取最新日志，点击"清空显示"清除当前面板内容。</p><p>注意：运行日志保存在内存环形缓冲中（最多 500 条），重启应用后清空。如需持久化日志请查看后端控制台输出。</p>', 12),

('筛选偏好设置', 'PriceTag', '<p>在系统设置中配置默认全局筛选列和条件预设模板：</p><p>1. 进入"系统设置"页面，点击左侧"筛选偏好"选项卡。</p><p>2. 默认全局筛选列：从字段映射表中选择需要常驻的列名（如 GR、AC），保存后进入异常测段提取的"所有文件"模式时自动加载。</p><p>3. 条件预设模板：点击"新建预设"输入名称，选择适用范围（全局/当前文件），勾选包含列并设定数值范围条件。</p><p>4. 保存后的预设会出现在列表中，支持编辑和删除。</p><p>5. 在异常测段提取页面打开筛选抽屉，点击"加载预设"按钮即可一键应用筛选条件。</p><p>6. 筛选抽屉中的"保存为预设"按钮可将当前筛选条件直接存为新预设。</p><p>注意：预设按用户隔离存储，不同用户之间的预设互不影响。更新预设后请点击"刷新"按钮同步列表。</p>', 13);

-- 4. 系统列名映射字典表 (动态解析归一化引擎)
CREATE TABLE IF NOT EXISTS `sys_column_mapping` (
  `id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT,
  `standard_key` VARCHAR(50) NOT NULL COMMENT '系统级关联键名(如 depth, ac)',
  `standard_name` VARCHAR(50) NOT NULL COMMENT '标准显示名(纯拼音/英文, 如 DEPTH, AC)',
  `chinese_meaning` VARCHAR(100) NOT NULL COMMENT '中文含义(如 深度, 声波, 用于前端展示与悬浮提示)',
  `alias_list` TEXT COMMENT '别名列表，逗号分隔 (如 dept,tvd,深度,测深,md)',
  `is_core` TINYINT(1) DEFAULT 0 COMMENT '是否为核心列(核心列无法直接被用户删除) 1-是 0-否',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_standard_key` (`standard_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统列名映射字典表';

INSERT IGNORE INTO `sys_column_mapping` (`standard_key`, `standard_name`, `chinese_meaning`, `alias_list`, `is_core`) VALUES
('depth', 'DEPTH', '测量深度', 'dept,tvd,深度,测深,md', 1),
('ac', 'AC', '声波时差', 'dt,_ac,声波,声波时差', 0),
('den', 'DEN', '岩石密度', 'zden,rhob,密度,岩石密度', 0),
('gr', 'GR', '自然伽马', '_gr,gamm,伽马,自然伽马', 0),
('sp', 'SP', '自然电位', '_sp,电位,自然电位', 0),
('rt', 'RT', '深电阻率', '_rt,ild,电阻率', 0),
-- ===== 补充常用测井字段映射 =====
('cal', 'CAL', '井径', 'cali,井径,caliper', 0),
('cnl', 'CNL', '补偿中子', 'nphi,中子,中子孔隙度', 0),
('pe', 'PE', '光电吸收截面', 'pef,光电', 0),
('r25', 'R25', '2.5m底部梯度电阻率', 'r2.5,r25m', 0),
('r4', 'R4', '4m底部梯度电阻率', 'r4m,r04', 0),
('rml', 'RML', '微电位电阻率', '微电位,rml', 0),
('rnml', 'RNML', '微梯度电阻率', '微梯度,rnml', 0),
('rs', 'RS', '浅侧向电阻率', '_rs,浅电阻率', 0),
('rxo', 'RXO', '冲洗带电阻率', '冲洗带,rxo', 0),
('lld', 'LLD', '深侧向电阻率', '深侧向,lld', 0),
('lls', 'LLS', '浅侧向电阻率', '浅侧向,lls', 0),
('msfl', 'MSFL', '微球聚焦电阻率', '微球,msfl', 0),
('bs', 'BS', '钻头直径', 'bit_size,钻头', 0),
('temp', 'TEMP', '井温', '温度,井温,temp', 0),
('tvd', 'TVD', '垂直深度', 'tvdss,垂深,垂直', 0),
-- 自然伽马能谱
('u', 'U', '铀含量', 'uranium,铀,uran', 0),
('k', 'K', '钾含量', 'potassium,钾,potas', 0),
('th', 'TH', '钍含量', 'thorium,钍', 0),
('thk', 'THK', '钍钾比', 'th_k,钍钾比,thkr', 0),
-- 电阻率补充
('ild', 'ILD', '深感应电阻率', '深感应,ild,induction_deep', 0),
('ilm', 'ILM', '中感应电阻率', '中感应,ilm,induction_med', 0),
('cond', 'COND', '感应电导率', '电导率,cond,conductivity', 0),
('r045', 'R045', '0.45m电位电阻率', 'r045,0.45m', 0),
('at10', 'AT10', '阵列感应10in', 'at10,阵列10', 0),
('at20', 'AT20', '阵列感应20in', 'at20,阵列20', 0),
('at30', 'AT30', '阵列感应30in', 'at30,阵列30', 0),
('at60', 'AT60', '阵列感应60in', 'at60,阵列60', 0),
('at90', 'AT90', '阵列感应90in', 'at90,阵列90', 0),
-- 孔隙度
('por', 'POR', '孔隙度', 'phi,porosity,孔隙度', 0),
('nphi', 'NPHI', '中子孔隙度', 'nphi_ls,nphi_ss,中子孔隙', 0),
('dphi', 'DPHI', '密度孔隙度', 'dphi,密度孔隙', 0),
-- 泥质/饱和度
('sh', 'SH', '泥质含量', 'vsh,shale,泥质,vshale', 0),
('sw', 'SW', '含水饱和度', 's_w,含水,swater', 0),
('so', 'SO', '含油饱和度', 's_o,含油,soil', 0),
('sxo', 'SXO', '冲洗带饱和度', 's_xo,冲洗带饱和度', 0),
-- 声波密度
('dts', 'DTS', '横波时差', 'dtsm,dshear,横波', 0),
('rhob', 'RHOB', '体积密度', 'rho,rhob,bulk,体积密度', 0),
('rhom', 'RHOM', '骨架密度', 'rhoma,matrix,骨架密度', 0),
-- 渗透率
('perm', 'PERM', '渗透率', 'permeability,渗透,perm', 0),
-- 井斜
('dev', 'DEV', '井斜角', 'deviation,井斜,inclination', 0),
('daz', 'DAZ', '井斜方位', 'dazi,azimuth,方位,方位角', 0);

-- 5. 解析失败的脏数据表
CREATE TABLE `log_dirty_data` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `file_id` BIGINT NOT NULL COMMENT '关联的文件ID',
  `line_num` INT NOT NULL COMMENT '文件中的原始行号',
  `raw_content` TEXT COMMENT '未能成功解析的原始文本行',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='解析失败的脏数据表';

-- 6. 刷新令牌表
CREATE TABLE IF NOT EXISTS `sys_refresh_token` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `token_hash` VARCHAR(64) NOT NULL COMMENT 'refresh token SHA-256哈希',
  `expires_at` DATETIME NOT NULL COMMENT 'refresh token过期时间',
  `revoked` TINYINT(1) DEFAULT 0 COMMENT '是否已撤销 0-否 1-是',
  `replaced_by_token_id` BIGINT DEFAULT NULL COMMENT '轮换后新token记录ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_token_hash` (`token_hash`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='刷新令牌表';

-- 9. 地质分层配置表
CREATE TABLE IF NOT EXISTS well_layer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_id BIGINT NOT NULL COMMENT '关联 log_file_info.id',
  layer_name VARCHAR(50) NOT NULL COMMENT '层位名称，如盒5、盒6',
  top_depth DECIMAL(10,4) NOT NULL COMMENT '顶深(m)',
  bottom_depth DECIMAL(10,4) NOT NULL COMMENT '底深(m)',
  remark VARCHAR(100) COMMENT '备注',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_file_depth (file_id, top_depth, bottom_depth)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地质分层配置表';

-- 10. 筛选条件配置表（默认全局筛选列 + 条件预设模板）
-- 用 type 字段区分两类配置，统一按 user_id 私有隔离：
--   default_columns: 用户固定的"默认全局筛选列"清单（每用户最多1条，name 固定为 __default__）
--   preset:          用户保存的"条件预设模板"（每用户N条，可命名、可增删）
CREATE TABLE IF NOT EXISTS `sys_filter_preset` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `type` VARCHAR(20) NOT NULL COMMENT '配置类型: default_columns默认列 / preset条件预设',
  `name` VARCHAR(50) NOT NULL COMMENT '名称，default_columns固定为__default__',
  `columns_json` TEXT COMMENT '列清单JSON: ["GR","AC","SP"]',
  `filters_json` TEXT COMMENT '数值条件JSON: {"GR":{"min":"120","max":""}}',
  `text_filters_json` TEXT COMMENT '文本条件JSON: {"岩性":["砂岩","泥岩"]}',
  `scope` VARCHAR(10) DEFAULT 'all' COMMENT 'preset专用: all全局 / current单文件',
  `sort_order` INT DEFAULT 0 COMMENT '排序号',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_user_type` (`user_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='筛选条件配置表（默认列+预设模板）';


