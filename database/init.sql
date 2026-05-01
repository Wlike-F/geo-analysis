-- 创建测井数据分析数据库
CREATE DATABASE IF NOT EXISTS `well_log_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `well_log_db`;

-- 1. 用户表
CREATE TABLE `sys_user` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码 (加密)',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名/网名',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态 (1正常, 0禁用)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统账号表';

-- 预置一名管理员 (密码: 123456 的MD5/Bcrypt等，视后端配置而定，此处为演示)
INSERT INTO `sys_user` (`username`, `password`, `real_name`) VALUES ('admin', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员');

-- 2. 上传/处理文件记录表
CREATE TABLE `log_file_record` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件名 (如: le7.txt)',
  `file_path` VARCHAR(500) NOT NULL COMMENT '本地绝对路径或云端URL',
  `load_type` VARCHAR(20) NOT NULL COMMENT '加载方式: UPLOAD上传, LOCAL_SCAN本地扫描',
  `line_count` INT(11) DEFAULT 0 COMMENT '解析行数',
  `create_by` BIGINT(20) NOT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加载时间',
  PRIMARY KEY (`id`),
  KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件处理记录表';

-- 3. 系统操作日志表
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
  
  -- 利用复合索引加速查询 --
  INDEX `idx_file_depth` (`file_id`, `depth`),
  INDEX `idx_file_main` (`file_id`, `ac`, `den`, `gr`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测井明细大宽表';


-- AI 智能问答会话表
CREATE TABLE ai_chat_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  title VARCHAR(255) COMMENT '会话标题',
  is_deleted TINYINT(1) DEFAULT 0 COMMENT '是否删除 0-否 1-是',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 智能问答会话表';

-- AI 智能问答消息表
CREATE TABLE ai_chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  session_id BIGINT NOT NULL COMMENT '会话ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role VARCHAR(50) NOT NULL COMMENT '角色: user/assistant',
  content TEXT COMMENT '消息内容',
  file_refs TEXT COMMENT '关联的文件(JSON数组类型)',
  is_deleted TINYINT(1) DEFAULT 0 COMMENT '是否删除 0-否 1-是',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 智能问答消息表';

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
('系统概览与核心能力', 'PriceTag', '本系统提供专业的地层分析、多通道并发比对、异常测段可视化等功能。目前处于稳定运行状态。', 1),
('测井数据看板使用提示', 'Lock', '在看板中您可以直观地浏览已上传的测井TXT文件数据，并可以通过顶部操作栏执行批量分析。', 2),
('提示 1：极速并发加载', 'Lightbulb', '当加载超过百万行的TXT文件时，系统会在后台起用流式多线程处理，界面会有短暂锁定期，请耐心等待。<br><img src="" alt="占位图片">', 3),
('提示 2：异常测段可视化', 'Lightbulb', '在异常测段可视化页面选择目标测井文件、显示通道、统计通道和异常识别条件，系统会生成多道曲线与异常测段高亮结果。异常测段可视化只用于分析展示与结果导出，不会修改原始解析数据。', 4),
('常见问题与解答', 'DocumentCopy', '<p><strong>Q：导出Excel失败怎么办？</strong></p><p>A：检查是否超出了Excel单表104万行的限制，如有需要请使用CSV格式。</p>', 5);
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
('rt', 'RT', '深电阻率', '_rt,ild,电阻率', 0);

CREATE TABLE `log_dirty_data` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `file_id` bigint NOT NULL COMMENT '关联的文件ID',
                                  `line_num` int NOT NULL COMMENT '文件中的原始行号',
                                  `raw_content` text COMMENT '未能成功解析的原始文本行',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`),
                                  KEY `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='解析失败的脏数据表';

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



