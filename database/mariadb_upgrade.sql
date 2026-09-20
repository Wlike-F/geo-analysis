-- ==================== MariaDB 数据库升级脚本（幂等，可重复执行） ====================
--
-- 适用环境：打包 exe 的 MariaDB 便携版（D:\geo-project\database\mysql）
-- 适用场景：客户机上的 MariaDB 库已存在历史数据，不能 DROP 重建，需要就地升级 schema
-- 执行方式：mysql -u root -p well_log_db < mariadb_upgrade.sql
--
-- ⚠️ 注意：此脚本依赖 MariaDB 扩展语法 `ADD COLUMN/INDEX IF NOT EXISTS`，
--          标准 MySQL（任何 8.x 版本）均不支持该语法，会报错。
--          开发环境若使用 MySQL，请直接用 init.sql 重建库，无需执行本脚本。
--
-- ======================================================================================

USE `well_log_db`;

-- ------------------------------------------------------------------
-- 1. log_data_records：补齐文本预留列 text_col_1 ~ text_col_10
--    以及对应的复合索引 idx_file_text1 ~ idx_file_text10
--    （历史版本仅有 text_col_1~5 及对应 5 个索引）
-- ------------------------------------------------------------------
ALTER TABLE `log_data_records`
  ADD COLUMN IF NOT EXISTS `text_col_1`  VARCHAR(200),
  ADD COLUMN IF NOT EXISTS `text_col_2`  VARCHAR(200),
  ADD COLUMN IF NOT EXISTS `text_col_3`  VARCHAR(200),
  ADD COLUMN IF NOT EXISTS `text_col_4`  VARCHAR(200),
  ADD COLUMN IF NOT EXISTS `text_col_5`  VARCHAR(200),
  ADD COLUMN IF NOT EXISTS `text_col_6`  VARCHAR(200),
  ADD COLUMN IF NOT EXISTS `text_col_7`  VARCHAR(200),
  ADD COLUMN IF NOT EXISTS `text_col_8`  VARCHAR(200),
  ADD COLUMN IF NOT EXISTS `text_col_9`  VARCHAR(200),
  ADD COLUMN IF NOT EXISTS `text_col_10` VARCHAR(200),
  ADD INDEX IF NOT EXISTS `idx_file_text1`  (`file_id`, `text_col_1`),
  ADD INDEX IF NOT EXISTS `idx_file_text2`  (`file_id`, `text_col_2`),
  ADD INDEX IF NOT EXISTS `idx_file_text3`  (`file_id`, `text_col_3`),
  ADD INDEX IF NOT EXISTS `idx_file_text4`  (`file_id`, `text_col_4`),
  ADD INDEX IF NOT EXISTS `idx_file_text5`  (`file_id`, `text_col_5`),
  ADD INDEX IF NOT EXISTS `idx_file_text6`  (`file_id`, `text_col_6`),
  ADD INDEX IF NOT EXISTS `idx_file_text7`  (`file_id`, `text_col_7`),
  ADD INDEX IF NOT EXISTS `idx_file_text8`  (`file_id`, `text_col_8`),
  ADD INDEX IF NOT EXISTS `idx_file_text9`  (`file_id`, `text_col_9`),
  ADD INDEX IF NOT EXISTS `idx_file_text10` (`file_id`, `text_col_10`);

-- 首次查询加速：纯 file_id 索引，COUNT / WHERE file_id=? 最快命中
ALTER TABLE `log_data_records` ADD INDEX IF NOT EXISTS `idx_file_id_simple` (`file_id`);

-- ------------------------------------------------------------------
-- 2. log_file_info：补齐文本列配置字段 text_columns_json
--    （JSON 对象：{原始列名: text_col_N}）
-- ------------------------------------------------------------------
ALTER TABLE `log_file_info`
  ADD COLUMN IF NOT EXISTS `text_columns_json` TEXT COMMENT '文本列配置，JSON对象{原始列名:text_col_N}';

ALTER TABLE `log_file_info`
  ADD COLUMN IF NOT EXISTS `column_stats_json` TEXT COMMENT '解析时预计算的核心列统计JSON，用于解析报告秒开';

-- ------------------------------------------------------------------
-- 3. 新建筛选条件配置表（默认全局筛选列 + 条件预设模板）
--    用 CREATE TABLE IF NOT EXISTS 保证幂等，新库已有此表时跳过
-- ------------------------------------------------------------------
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

-- ==================== 补充使用说明条目（幂等，INSERT IGNORE 可重复执行） ====================
INSERT IGNORE INTO sys_instruction (title, icon, content, sort_order) VALUES
('地质分层配置', 'PriceTag', '<p>为测井文件配置地层分段（层名、顶深、底深）：</p><p>1. 在"数据源管理"页面找到目标文件，点击操作列的"分层"按钮打开分层配置对话框。</p><p>2. 手动输入：在可编辑表格中逐行填写层位名称、顶深（m）、底深（m）和备注，点击"保存分层配置"。</p><p>3. 从文件导入：点击"从文件导入"上传 Excel/CSV/TXT 文件，系统自动识别表头中的"层"、"顶"、"底"关键词定位列。</p><p>4. 如果导入文件包含多口井的数据，系统会弹出井名选择对话框，选择当前文件对应的井名后再导入。</p><p>5. 跨文件复制：配置好一口井后点击"复制到其他文件"，勾选同区块其他井，层名结构会被复制。</p><p>6. 配置完成后，导出 Excel 时会自动追加"层位"列；异常测段提取时如果测段跨越分层边界会自动切割为多段。</p><p>注意：分层边界采用左闭右开规则：顶深 ≤ depth < 底深。建议下一层的顶深 = 上一层的底深，避免重叠或空隙。</p>', 11),
('运行日志查看', 'Bell', '<p>在系统设置中实时查看后端服务的运行日志：</p><p>1. 进入"系统设置"页面，点击左侧导航的"运行日志"选项卡。</p><p>2. 日志面板以深色终端风格显示后端实时日志，包括时间戳、日志级别、来源类名和消息内容。</p><p>3. 通过顶部下拉框可按级别筛选：ALL、INFO、WARN、ERROR。</p><p>4. 日志每 3 秒自动刷新，新日志自动滚动到底部。</p><p>5. 点击"刷新日志"可立即手动拉取最新日志，点击"清空显示"清除当前面板内容。</p><p>注意：运行日志保存在内存环形缓冲中（最多 500 条），重启应用后清空。如需持久化日志请查看后端控制台输出。</p>', 12),
('筛选偏好设置', 'PriceTag', '<p>在系统设置中配置默认全局筛选列和条件预设模板：</p><p>1. 进入"系统设置"页面，点击左侧"筛选偏好"选项卡。</p><p>2. 默认全局筛选列：从字段映射表中选择需要常驻的列名（如 GR、AC），保存后进入异常测段提取的"所有文件"模式时自动加载。</p><p>3. 条件预设模板：点击"新建预设"输入名称，选择适用范围（全局/当前文件），勾选包含列并设定数值范围条件。</p><p>4. 保存后的预设会出现在列表中，支持编辑和删除。</p><p>5. 在异常测段提取页面打开筛选抽屉，点击"加载预设"按钮即可一键应用筛选条件。</p><p>6. 筛选抽屉中的"保存为预设"按钮可将当前筛选条件直接存为新预设。</p><p>注意：预设按用户隔离存储，不同用户之间的预设互不影响。更新预设后请点击"刷新"按钮同步列表。</p>', 13);

-- ==================== 补充常用测井字段映射（幂等） ====================
INSERT IGNORE INTO `sys_column_mapping` (`standard_key`, `standard_name`, `chinese_meaning`, `alias_list`, `is_core`) VALUES
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

