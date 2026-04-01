# 测井数据列名解析与多文件筛选架构重构方案 (详细版)

本方案详细说明了数据异构解析核心问题（不同文件列名不一致导致的查询失效），并提供了带“标准显示名”与“中文含义”的参数字典表映射机制的完整执行规范。

## 一、 核心痛点回顾
在处理不同采集设备或不同时期留存的测井TXT文件时，同一物理含义的列名会出现大量变体。
例如：
* **深度**：`DEPTH`, `dept`, `tvd`, `深度`, `测深`, `md`
* **声波**：`AC`, `dt`, `_ac`, `声波`
* **密度**：`DEN`, `zden`, `rhob`, `密度`

如果在系统载入时不进行标准化映射，前端多文件筛选框将出现“列名爆炸”（如10余个不知名属性），并且在跨文件条件过滤时，会出现“互相封杀”（文件A有DEPTH没深度，文件B有深度没DEPTH，联合查询全部返回空数据）的严重Bug。

## 二、 核心解决方案：字典映射引擎 (Dictionary Mapping Engine)

建立一个 `sys_column_mapping` 字典表，由用户（管理员）在前端动态维护。
系统在解析 TXT 头信息时，将其通过字典表统一“洗注”为标准键名存入数据库和通知前端。

### 1. 字典表设计 (`sys_column_mapping`)
* **`standard_key`**: 系统的底层标准键名 (如 `DEPTH`, `AC`, `DEN` 等唯一标识)。
* **`standard_name`**: 标准显示名 (纯英文/拼音标准，推荐大写英文字母)。
* **`chinese_meaning`**: 中文含义 (如 “深度”, “声波”, “密度”，只用于前端用户提示与展示)。
* **`alias_list`**: 异构前缀/后缀别名列表，使用逗号分隔 (如 `dept,tvd,深度,测深,md`)。
* **`is_core`**: 是否为系统核心关联列（如 DEPTH 作为全系统比对基准，为核心列，不可删除）。

### 2. 数据库结构 (SQL)
在 `init.sql` 中新增以下结构：

```sql
CREATE TABLE IF NOT EXISTS `sys_column_mapping` (
  `id` BIGINT(20) PRIMARY KEY AUTO_INCREMENT,
  `standard_key` VARCHAR(50) NOT NULL COMMENT '系统级关联键名(如 depth, ac)',
  `standard_name` VARCHAR(50) NOT NULL COMMENT '标准显示名(如 DEPTH, AC)',
  `chinese_meaning` VARCHAR(100) NOT NULL COMMENT '中文含义(如 深度, 声波)',
  `alias_list` TEXT COMMENT '别名列表，逗号分隔 (如 dept,tvd,深度,测深,md)',
  `is_core` TINYINT(1) DEFAULT 0 COMMENT '是否为核心列(核心列无法被用户删除) 1-是 0-否',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_standard_key` (`standard_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统列名映射字典表';

-- 预置核心数据映射字典
INSERT IGNORE INTO `sys_column_mapping` (`standard_key`, `standard_name`, `chinese_meaning`, `alias_list`, `is_core`) VALUES
('depth', 'DEPTH', '测量深度', 'dept,tvd,深度,测深,md', 1),
('ac', 'AC', '声波时差', 'dt,_ac,声波,声波时差', 0),
('den', 'DEN', '岩石密度', 'zden,rhob,密度,岩石密度', 0),
('gr', 'GR', '自然伽马', '_gr,gamm,伽马,自然伽马', 0),
('sp', 'SP', '自然电位', '_sp,电位,自然电位', 0),
('rt', 'RT', '深电阻率', '_rt,ild,电阻率', 0);
```

### 3. 数据流程改造
1. **上传解析阶段**：上传 TXT 文件，读取第一行 `Headers`。
2. **字典匹配算法**：遍历 Headers 数组，在 `sys_column_mapping` 内存缓存中匹配 `alias_list`。
3. **更名与入库**：一旦匹配命中，将文件头字段替换为 `standard_key`。未能匹配系统字段时，归为原始表头。
4. **前端筛选渲染**：提供“字典管理”入口供用户维护清洗规则。文件列表渲染时，直接展示洗注后的 `standard_name` 和 `chinese_meaning`。