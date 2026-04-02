# 地质测井分析系统 - 海量数据存储与分析架构升级方案

## 1. 架构目标
当前系统架构将测井文件（包含可达几十万至八十万行记录）的数据直接放置于内存并全部抛给前端进行渲染与运算。为了向真正的“商用级”大数据分析工具进化，我们需要引入基于**临时生命周期管理的大宽表存储机制**。从而实现：
1. **解除内存与渲染瓶颈**：不再将海量数据扔给前端，前端仅作分页获取与局部刷新。
2. **极速的阈值筛选**：将多列数据区间阈值的比对，转化为 MySQL 高效索引查询。
3. **数据隔离与优雅释放**：实现物理层面的数据安全隔离，辅以自动定时清理任务，避免数据库体积无限膨胀。

---

## 2. 核心数据建模设计

我们将放弃过去“不入库”或“按文件建表”的做法。
我们采用**“索引指向 + 海量宽表”**的数据架构，将文件主信息与行级数据拆分开。

### 2.1 文件主表 (File Metadata)
用于记录每次上传事件及其状态、文件头结构信息。
```sql
CREATE TABLE `log_file_info` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '上传者ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件原始名称',
  `columns_json` TEXT COMMENT '动态列名配置，JSON数组格式保存',
  `total_rows` INT DEFAULT 0 COMMENT '总解析行数',
  `status` TINYINT DEFAULT 1 COMMENT '1:正常 2:逻辑删除，待定时任务物理清除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测井文件上传主表';
```

### 2.2 测井数据大宽表 (Log Data Records)
鉴于 TXT 的列数已经动态化，为了兼容最大灵活性，建议设计一张包含充足预留列或基于 JSON 型支持的大表，也可保留动态建列架构：
```sql
CREATE TABLE `log_data_records` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `file_id` BIGINT NOT NULL COMMENT '关联log_file_info表的ID',
  `depth` DECIMAL(10,4) COMMENT '深度（核心比对条件）',
  -- 静态核心列 --
  `ac` DECIMAL(10,4),
  `den` DECIMAL(10,4),
  `gr` DECIMAL(10,4),
  -- 动态拓展列，满足“任意多少列都展示”的需求 --
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
```

---

## 3. 生命周期管理与清理机制（TTL）

将数据库作为“缓存池”，杜绝永远堆积数据的问题：

### 3.1 自动定时清理任务触发 (@Scheduled)
在 Spring Boot 端利用 Quartz 添加一个自动调度任务。
```java
@Component
@Slf4j
public class DataCleanupTask {
    @Autowired
    private LogDataRecordMapper dataRecordMapper;
    @Autowired
    private LogFileInfoMapper fileInfoMapper;

    // 每天凌晨 3:00 执行清理
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredData() {
        log.info("开始执行测井历史数据定时清理任务...");
        // 1. 获取超过 7 天的文件ID
        LocalDateTime expireTime = LocalDateTime.now().minusDays(7);
        List<Long> expiredFileIds = fileInfoMapper.selectExpiredFileIds(expireTime);
        
        if(!expiredFileIds.isEmpty()) {
            // 2. 分批次物理删除宽表数据（防止大事务锁表）
            dataRecordMapper.deleteByFileIdsInBatches(expiredFileIds);
            // 3. 删除主文件记录
            fileInfoMapper.deleteBatchIds(expiredFileIds);
            log.info("已清理 {} 个过期文件的海量明细数据", expiredFileIds.size());
        }
    }
}
```

### 3.2 用户主动销毁按钮
在前端页面中添加**“关闭该项目/释放内存”**按钮。点击后，后端可将对应 `file_id` 在 `log_file_info` 中标记为逻辑删除，甚至直接触发异步的物理删除。

---

## 4. 前后端流转机制重构方案

### 4.1 Java 端插入机制：极速批量入库
对于数十万行的 txt 文件，通过 MyBatis-Plus 的 `saveBatch` 方案，以 5,000 ~ 10,000 条为一块（Chunk）进行分批写入。
```java
// 伪代码示例：分批提交避免内存耗尽
List<LogDataRecords> batchList = new ArrayList<>();
for (String line : lines) {
    LogDataRecords record = parseLine(line);
    batchList.add(record);
    if (batchList.size() >= 5000) {
        logDataService.saveBatch(batchList);
        batchList.clear();
    }
}
if (!batchList.isEmpty()) {
    logDataService.saveBatch(batchList); // 处理尾部数据
}
```

### 4.2 前端拉取机制：分页化查询
绝不能再次 `SELECT *` 给到前端。前端 `el-table` 应绑定到分页组件 `<el-pagination>`。
筛选条件发生变化（例如 `DEPTH: 100 - 200`）时，只下发给后端拼接 SQL `WHERE file_id=? AND depth BETWEEN 100 AND 200 LIMIT 0, 50`。

### 4.3 异步下载与服务器导出
在下载全量分析报告时，改为**异步下载模型**：
1. 用户点击“生成 Excel”。
2. 后端基于指定 `file_id` 读取全量条件，利用 **EasyExcel 结合流式数据读取 (Scroll Query/Cursor)** 进行无内存占用边写边落盘。
3. 后端生成 `.xlsx` 返回给前端对应的文件流或下载URL链接。

---

## 5. 可选替代路线（备忘，暂时不用）

若未来 MySQL 的 CPU/IO 被测井分析极度消耗影响其余业务组件，可切换至以下架构：
* **集成 DuckDB**：为 OLAP 分析而生的内存数据库，无需配置复杂表结构，后端接收 TXT 并转为 Parquet 后，直接通过 DuckDB 的 JDBC 快速聚合。
* **SQLite 物理隔离**：对每次文件访问实时 `jdbc:sqlite:{uuid}.db`，使用后 `File.delete()`。
