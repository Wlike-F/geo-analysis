package com.xy.welllog.controller;

import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xy.welllog.common.Result;
import com.xy.welllog.dto.LogDataQueryDTO;
import com.xy.welllog.entity.LogDataRecord;
import com.xy.welllog.entity.LogFileInfo;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.service.LogDataRecordService;
import com.xy.welllog.service.LogFileInfoService;
import com.xy.welllog.service.SysOperationLogService;
import com.xy.welllog.service.SysUserService;
import com.xy.welllog.service.WellLayerService;
import com.xy.welllog.entity.WellLayer;
import com.xy.welllog.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/data")
@Slf4j
public class LogDataController {

    @Autowired
    private LogDataRecordService dataRecordService;

    @Autowired
    private LogFileInfoService fileInfoService;

    @Autowired
    private SysOperationLogService operationLogService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private WellLayerService wellLayerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LogDataRecordService logDataRecordService;

    /** 每批导出行数（受限于 MybatisPlus 分页上限 10000） */
    private static final int EXPORT_BATCH_SIZE = 10000;
    /** 单次导出最大行数（xlsx 格式上限 1,048,576，留余量给表头） */
    private static final int EXPORT_MAX_TOTAL = 500000;

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String username = jwtUtils.getUsernameFromToken(header.substring(7));
            if (username != null) {
                LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SysUser::getUsername, username);
                SysUser user = sysUserService.getOne(wrapper);
                if (user != null) {
                    return user.getId();
                }
            }
        }
        return null;
    }

    /** 校验文件所有权，不匹配时抛异常 */
    private void checkFileOwnership(Long fileId, Long userId) {
        if (userId == null) throw new RuntimeException("未登录");
        LogFileInfo fi = fileInfoService.getById(fileId);
        checkFileOwnership(fi, userId);
    }
    private void checkFileOwnership(LogFileInfo fi, Long userId) {
        if (userId == null) throw new RuntimeException("未登录");
        if (fi == null || !fi.getUserId().equals(userId))
            throw new RuntimeException("无权访问该文件");
    }

    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> pageQuery(@RequestBody LogDataQueryDTO query, HttpServletRequest request) {
        Long userId = getUserId(request);
        checkFileOwnership(query.getFileId(), userId);
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null || query.getSize() < 1 ? 100L : Math.min(query.getSize(), 10000L);

        // 一次查询 fileInfo，全方法复用
        LogFileInfo fileInfo = fileInfoService.getById(query.getFileId());
        List<String> cols = new ArrayList<>();
        Map<String, String> textColMapping = Collections.emptyMap();
        if (fileInfo != null) {
            if (fileInfo.getColumnsJson() != null) {
                cols = JSONUtil.toList(fileInfo.getColumnsJson(), String.class);
            }
            if (fileInfo.getTextColumnsJson() != null) {
                textColMapping = JSONUtil.toBean(fileInfo.getTextColumnsJson(), Map.class);
            }
        }

        // 预计算列→取值函数，避免逐行 if/else
        Map<String, java.util.function.Function<LogDataRecord, Object>> columnReaders = buildColumnReaders(cols);

        Page<LogDataRecord> pageParam = new Page<>(current, size);
        QueryWrapper<LogDataRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("file_id", query.getFileId());

        if (query.getFilters() != null && !query.getFilters().isEmpty()) {
            for (Map.Entry<String, LogDataQueryDTO.FilterRange> entry : query.getFilters().entrySet()) {
                String col = entry.getKey();
                LogDataQueryDTO.FilterRange range = entry.getValue();
                if (range == null) continue;

                // 文本列筛选
                if (range.getValues() != null && !range.getValues().isEmpty()) {
                    String textCol = resolveTextCol(textColMapping, col);
                    if (textCol != null) {
                        wrapper.in(textCol, range.getValues());
                    }
                    continue;
                }

                // 定量列筛选
                if (range.getMin() == null && range.getMax() == null) continue;
                String dbCol = getMappedDbColumn(col.toLowerCase());
                if (dbCol.equals("unmapped")) {
                    if (range.getMin() != null) wrapper.apply("JSON_EXTRACT(extra_json, CONCAT('$.', {0})) >= {1}", col, range.getMin());
                    if (range.getMax() != null) wrapper.apply("JSON_EXTRACT(extra_json, CONCAT('$.', {0})) <= {1}", col, range.getMax());
                } else {
                    if (range.getMin() != null) wrapper.ge(dbCol, range.getMin());
                    if (range.getMax() != null) wrapper.le(dbCol, range.getMax());
                }
            }
        }

        wrapper.orderByAsc("id");
        // 无筛选条件时跳过 COUNT（用 fileInfo.totalRows 代替），首次加载 + 预览瞬时
        boolean hasFilters = query.getFilters() != null && !query.getFilters().isEmpty();
        if (!hasFilters) {
            pageParam.setSearchCount(false);
        }
        Page<LogDataRecord> recordPage = dataRecordService.page(pageParam, wrapper);

        // 文本列值补充：因 select=false，MyBatis-Plus 不查 text_col_N，用 JdbcTemplate 单独取
        Map<Long, Map<String, String>> textColValues = Collections.emptyMap();
        if (!textColMapping.isEmpty() && !recordPage.getRecords().isEmpty()) {
            textColValues = loadTextColumnValues(recordPage.getRecords(), textColMapping);
        }

        // 无筛选时总数用元数据（瞬时），有筛选时用实查 COUNT
        long total = hasFilters ? recordPage.getTotal() : (fileInfo != null && fileInfo.getTotalRows() != null ? fileInfo.getTotalRows() : recordPage.getTotal());
        List<Map<String, Object>> mapList = new ArrayList<>(recordPage.getRecords().size());
        for (LogDataRecord record : recordPage.getRecords()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", record.getId());
            Map<String, String> textVals = textColValues.getOrDefault(record.getId(), Collections.emptyMap());
            for (String colName : cols) {
                // 文本列优先从补充查询取值，其次走 reader
                String textVal = textVals.get(colName);
                if (textVal != null) {
                    map.put(colName, textVal);
                } else {
                    java.util.function.Function<LogDataRecord, Object> reader = columnReaders.get(colName);
                    Object val = reader != null ? reader.apply(record) : null;
                    map.put(colName, val != null ? val : "");
                }
            }
            if (record.getExtraJson() != null && !record.getExtraJson().isEmpty()) {
                map.putAll(record.getExtraJson());
            }
            mapList.add(map);
        }

        Page<Map<String, Object>> resultPage = new Page<>(current, size, total);
        resultPage.setRecords(mapList);
        return Result.success(resultPage);
    }

    /** 预计算列名→实体取值函数，一次构建，百次复用 */
    private Map<String, java.util.function.Function<LogDataRecord, Object>> buildColumnReaders(List<String> cols) {
        Map<String, java.util.function.Function<LogDataRecord, Object>> readers = new LinkedHashMap<>();
        for (String col : cols) {
            String lower = col.toLowerCase();
            if (lower.contains("depth") || lower.contains("tvd") || lower.contains("dep")) {
                readers.put(col, LogDataRecord::getDepth);
            } else {
                switch (lower) {
                    case "ac": readers.put(col, LogDataRecord::getAc); break;
                    case "den": readers.put(col, LogDataRecord::getDen); break;
                    case "gr": readers.put(col, LogDataRecord::getGr); break;
                    case "sp": readers.put(col, LogDataRecord::getSp); break;
                    case "rt": readers.put(col, LogDataRecord::getRt); break;
                    default: readers.put(col, r -> null); break;
                }
            }
        }
        return readers;
    }

    private java.util.function.Function<LogDataRecord, Object> textColGetter(String colName) {
        switch (colName) {
            case "text_col_1": return LogDataRecord::getTextCol1;
            case "text_col_2": return LogDataRecord::getTextCol2;
            case "text_col_3": return LogDataRecord::getTextCol3;
            case "text_col_4": return LogDataRecord::getTextCol4;
            case "text_col_5": return LogDataRecord::getTextCol5;
            case "text_col_6": return LogDataRecord::getTextCol6;
            case "text_col_7": return LogDataRecord::getTextCol7;
            case "text_col_8": return LogDataRecord::getTextCol8;
            case "text_col_9": return LogDataRecord::getTextCol9;
            case "text_col_10": return LogDataRecord::getTextCol10;
            default: return r -> null;
        }
    }

    /** 从已缓存的 textColMapping 中解析文本列映射，不再查 DB */
    private String resolveTextCol(Map<String, String> mapping, String col) {
        if (mapping.isEmpty()) return null;
        String v = mapping.get(col);
        if (v == null) v = mapping.get(col.toLowerCase());
        if (v == null) v = mapping.get(col.toUpperCase());
        return (v != null && isValidTextCol(v)) ? v : null;
    }

    /** 批量查询文本列值（兼容 select=false 的旧数据库，text_col_N 不在 MyBatis-Plus SELECT 中） */
    private Map<Long, Map<String, String>> loadTextColumnValues(
            List<LogDataRecord> records, Map<String, String> textColMapping) {
        if (records.isEmpty() || textColMapping.isEmpty()) return Collections.emptyMap();
        String ids = records.stream().map(r -> String.valueOf(r.getId())).collect(java.util.stream.Collectors.joining(","));
        Map<Long, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : textColMapping.entrySet()) {
            String colName = e.getKey();
            String textCol = e.getValue();
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, " + textCol + " FROM log_data_records WHERE id IN (" + ids + ")");
                for (Map<String, Object> row : rows) {
                    Long id = ((Number) row.get("id")).longValue();
                    Object val = row.get(textCol);
                    result.computeIfAbsent(id, k -> new LinkedHashMap<>())
                          .put(colName, val != null ? String.valueOf(val) : "");
                }
            } catch (Exception ex) {
                log.warn("[查询] 文本列 {} 查询失败: {}", textCol, ex.getMessage());
            }
        }
        return result;
    }

    private String getMappedDbColumn(String colLower) {
        if (colLower.equals("depth") || colLower.contains("tvd") || colLower.contains("dep")) return "depth";
        return switch (colLower) {
            case "ac" -> "ac";
            case "den" -> "den";
            case "gr" -> "gr";
            case "sp" -> "sp";
            case "rt" -> "rt";
            default -> "unmapped";
        };
    }

    /**
     * 校验 text_col 列名是否为合法的 text_col_1 ~ text_col_10，防止 SQL 注入
     */
    private static final java.util.regex.Pattern TEXT_COL_PATTERN =
            java.util.regex.Pattern.compile("^text_col_([1-9]|10)$");

    private boolean isValidTextCol(String columnName) {
        return columnName != null && TEXT_COL_PATTERN.matcher(columnName).matches();
    }

    /**
     * 根据文件的 text_columns_json 配置，获取原始列名对应的 text_col_N 数据库字段。
     * 返回结果经过白名单校验，不合法的列名返回 null。
     */
    private String getTextDbColumnFrom(LogFileInfo fileInfo, String originalColName) {
        if (fileInfo == null || fileInfo.getTextColumnsJson() == null) return null;
        Map<String, String> mapping = JSONUtil.toBean(fileInfo.getTextColumnsJson(), Map.class);
        String mapped = mapping.get(originalColName);
        if (mapped == null) mapped = mapping.get(originalColName.toLowerCase());
        if (mapped == null) mapped = mapping.get(originalColName.toUpperCase());
        if (mapped != null && !isValidTextCol(mapped)) return null;
        return mapped;
    }

    private String getTextDbColumn(Long fileId, String originalColName) {
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null || fileInfo.getTextColumnsJson() == null) return null;
        Map<String, String> mapping = JSONUtil.toBean(fileInfo.getTextColumnsJson(), Map.class);
        // 尝试多种大小写匹配
        String mapped = mapping.get(originalColName);
        if (mapped == null) mapped = mapping.get(originalColName.toLowerCase());
        if (mapped == null) mapped = mapping.get(originalColName.toUpperCase());
        // 白名单校验：只允许 text_col_1 ~ text_col_10
        if (mapped != null && !isValidTextCol(mapped)) {
            log.warn("[安全] 拦截非法 text_col 列名: {} -> {}", originalColName, mapped);
            return null;
        }
        return mapped;
    }

    /**
     * 获取文本列的唯一值列表（用于前端多选下拉）
     */
    @GetMapping("/{fileId}/distinct-values")
    public Result<List<String>> getDistinctValues(@PathVariable Long fileId,
                                                   @RequestParam String column,
                                                   HttpServletRequest request) {
        Long userId = getUserId(request);
        checkFileOwnership(fileId, userId);
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) return Result.failed("文件不存在");

        String dbColumn = getTextDbColumnFrom(fileInfo, column);
        if (dbColumn == null) {
            return Result.failed("列 [" + column + "] 未配置为文本列");
        }

        // 用 JdbcTemplate 显式查询（text_col_N 标记了 select=false，MyBatis-Plus 不会自动选）
        String sql = "SELECT DISTINCT " + dbColumn + " FROM log_data_records WHERE file_id = ? AND " + dbColumn + " IS NOT NULL AND " + dbColumn + " != '' ORDER BY " + dbColumn + " LIMIT 200";
        List<String> values = jdbcTemplate.queryForList(sql, String.class, fileId);
        return Result.success(values);
    }

    /**
     * 保存文件的文本列配置，并从 extra_json 回填数据到 text_col_N 字段
     */
    /**
     * 扫描候选文本列（前500行分析）
     */
    @GetMapping("/{fileId}/text-columns/candidates")
    public Result<List<Map<String, Object>>> scanTextColumnCandidates(@PathVariable Long fileId,
                                                                       HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) return Result.failed("文件不存在");
        checkFileOwnership(fileInfo, userId);

        String columnsJson = fileInfo.getColumnsJson();
        if (columnsJson == null || columnsJson.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<String> columns = JSONUtil.toList(columnsJson, String.class);
        if (columns == null || columns.isEmpty()) return Result.success(Collections.emptyList());

        // 随机采样前500行
        List<LogDataRecord> sample = logDataRecordService.lambdaQuery()
                .eq(LogDataRecord::getFileId, fileId)
                .last("LIMIT 500")
                .list();

        List<Map<String, Object>> result = new ArrayList<>();
        for (String col : columns) {
            int total = 0, nonNumeric = 0;
            Set<String> uniqueValues = new LinkedHashSet<>();
            List<String> samples = new ArrayList<>();

            for (LogDataRecord row : sample) {
                String val = readColumnValue(row, col);
                if (val == null || val.isEmpty()) continue;
                total++;
                if (!isNumeric(val)) nonNumeric++;
                if (uniqueValues.size() < 500) uniqueValues.add(val);
                if (samples.size() < 3) samples.add(val);
            }

            if (total == 0) continue;

            double nonNumericRate = Math.round(nonNumeric * 10000.0 / total) / 100.0;
            int uniqueCount = uniqueValues.size();
            String reason = null;
            boolean suggested = false;

            // 判定规则
            boolean nameHint = col.matches("(?i).*(岩性|地层|层位|解释|结论|lith|formation|layer|text|desc|facies).*");
            if (nameHint && nonNumericRate > 50) {
                reason = "列名+数据类型";
                suggested = true;
            } else if (nonNumericRate > 70 && uniqueCount >= 2 && uniqueCount <= 200) {
                reason = "自动检测";
                suggested = true;
            } else if (nonNumericRate > 70) {
                reason = "自动检测";
                suggested = false; // 值太多，不建议默认勾选
            }

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", col);
            info.put("nonNumericRate", nonNumericRate);
            info.put("uniqueCount", uniqueCount);
            info.put("samples", samples);
            info.put("suggested", suggested);
            info.put("reason", reason);
            result.add(info);
        }
        return Result.success(result);
    }

    @PostMapping("/{fileId}/text-columns")
    public Result<String> saveTextColumns(@PathVariable Long fileId,
                                           @RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) return Result.failed("文件不存在");
        checkFileOwnership(fileInfo, userId);

        // 支持两种格式: {columns: ["岩性","解释结论"]} 或 {原始列名: text_col_N}
        Map<String, String> mapping = new LinkedHashMap<>();
        Object columnsObj = body.get("columns");
        if (columnsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> colList = (List<String>) columnsObj;
            if (colList.size() > 10) return Result.failed("文本列最多支持 10 个");
            for (int i = 0; i < colList.size(); i++) {
                mapping.put(colList.get(i), "text_col_" + (i + 1));
            }
        } else {
            // 兼容旧格式
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                Object v = entry.getValue();
                if (v instanceof String && isValidTextCol((String) v)) {
                    mapping.put(entry.getKey(), (String) v);
                }
            }
            if (mapping.size() > 10) return Result.failed("文本列最多支持 10 个");
        }

        // 白名单校验
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (!isValidTextCol(entry.getValue())) {
                return Result.failed("列名不合法：" + entry.getValue());
            }
        }

        fileInfo.setTextColumnsJson(JSONUtil.toJsonStr(mapping));
        fileInfoService.updateById(fileInfo);

        // 多列并行回填（每列限制 50000 行/批，各列独立线程）
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String originalCol = entry.getKey();
            String textCol = entry.getValue();
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String jsonPath = "$." + originalCol;
                    String sql = "UPDATE log_data_records SET " + textCol +
                            " = JSON_UNQUOTE(JSON_EXTRACT(extra_json, ?))" +
                            " WHERE file_id = ?" +
                            " AND JSON_EXTRACT(extra_json, ?) IS NOT NULL" +
                            " AND JSON_UNQUOTE(JSON_EXTRACT(extra_json, ?)) != ''" +
                            " LIMIT 50000";
                    int batchUpdated, totalUpdated = 0;
                    do {
                        batchUpdated = jdbcTemplate.update(sql, jsonPath, fileId, jsonPath, jsonPath);
                        totalUpdated += batchUpdated;
                    } while (batchUpdated >= 50000);
                    log.info("[筛选] 回填 {} -> {}: fileId={}, 更新 {} 行", originalCol, textCol, fileId, totalUpdated);
                    return true;
                } catch (Exception e) {
                    log.warn("[筛选] 回填失败 {} -> {}: {}", originalCol, textCol, e.getMessage());
                    return false;
                }
            }));
        }

        boolean allSuccess = futures.stream()
                .map(f -> { try { return f.get(120, TimeUnit.SECONDS); } catch (Exception e) { return false; } })
                .allMatch(Boolean.TRUE::equals);

        return Result.success(allSuccess
                ? "文本列配置已保存，数据回填完成"
                : "文本列配置已保存，但部分回填失败");
    }

    /**
     * 获取文件的文本列配置
     */
    @GetMapping("/{fileId}/text-columns")
    public Result<Map<String, String>> getTextColumns(@PathVariable Long fileId,
                                                        HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) return Result.failed("文件不存在");
        checkFileOwnership(fileInfo, userId);

        if (fileInfo.getTextColumnsJson() == null || fileInfo.getTextColumnsJson().isEmpty()) {
            return Result.success(new LinkedHashMap<>());
        }
        Map<String, String> mapping = JSONUtil.toBean(fileInfo.getTextColumnsJson(), Map.class);
        return Result.success(mapping);
    }

    @PostMapping("/export-batch-zip")
    public void exportBatchZip(@RequestBody List<LogDataQueryDTO> queries, HttpServletRequest request, HttpServletResponse response) {
        try {
            Long userId = getUserId(request);
            response.setContentType("application/zip");
            response.setCharacterEncoding("utf-8");
            String zipName = URLEncoder.encode("批量测井数据导出", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + zipName + ".zip");

            // 阶段1：并行生成每个文件的 Excel byte[]
            List<CompletableFuture<ZipPart>> futures = new ArrayList<>();
            for (LogDataQueryDTO query : queries) {
                if (query.getFileId() == null) continue;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    LogFileInfo fileInfo = fileInfoService.getById(query.getFileId());
                    if (fileInfo == null) return null;
                    List<String> cols = getColumns(query.getFileId());
                    if (cols.isEmpty()) return null;
                    String fileName = (fileInfo.getFileName() != null ? fileInfo.getFileName() : ("File_" + query.getFileId() + ".txt"))
                            .replaceAll("[\\\\/?*:\\[\\]]", "_") + ".xlsx";

                    List<List<String>> head = buildExportHead(cols, query.getFileId());
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ExcelWriter excelWriter = EasyExcel.write(bos).head(head).autoCloseStream(Boolean.FALSE).build();
                    WriteSheet sheet = EasyExcel.writerSheet("Filtered Data").build();
                    long lineCount = streamWriteData(excelWriter, sheet, query.getFileId(), query, cols);
                    excelWriter.finish();
                    return new ZipPart(fileName, bos.toByteArray(), lineCount);
                }));
            }

            // 阶段2：串行写入 ZIP
            int exportedFileCount = 0;
            long exportedLineCount = 0L;
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
                for (CompletableFuture<ZipPart> f : futures) {
                    ZipPart part = f.get(600, TimeUnit.SECONDS);
                    if (part == null) continue;
                    zos.putNextEntry(new java.util.zip.ZipEntry(part.name));
                    zos.write(part.bytes);
                    zos.closeEntry();
                    exportedFileCount++;
                    exportedLineCount += part.lineCount;
                }
                zos.finish();
            }

            operationLogService.recordLog("报表导出", "筛选结果批量ZIP导出", exportedFileCount, exportedLineCount, userId);
            log.info("[导出] 批量ZIP导出完成: {} 个文件, {} 行", exportedFileCount, exportedLineCount);
        } catch (Exception e) {
            log.error("[导出] 批量ZIP导出异常", e);
            response.setStatus(500);
        }
    }

    private List<List<String>> buildExportHead(List<String> cols, Long fileId) {
        List<List<String>> head = new ArrayList<>();
        for (String col : cols) head.add(Collections.singletonList(col));
        long layerCount = wellLayerService.count(
                new LambdaQueryWrapper<WellLayer>().eq(WellLayer::getFileId, fileId));
        if (layerCount > 0) head.add(Collections.singletonList("层位"));
        return head;
    }

    /** ZIP 分片：文件名 + Excel 字节 + 行数 */
    private static class ZipPart {
        final String name;
        final byte[] bytes;
        final long lineCount;
        ZipPart(String n, byte[] b, long l) { name = n; bytes = b; lineCount = l; }
    }

    @PostMapping("/{fileId}/export")
    public void exportExcel(@PathVariable Long fileId, @RequestBody(required = false) LogDataQueryDTO query, HttpServletRequest request, HttpServletResponse response) {
        if (query == null) query = new LogDataQueryDTO();
        query.setFileId(fileId);
        checkFileOwnership(fileId, getUserId(request));

        List<String> cols = getColumns(fileId);
        log.info("[导出] 开始Excel导出: fileId={}, 列数={}", fileId, cols.size());

        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("Filtered_Data_" + fileId, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            List<List<String>> head = new ArrayList<>();
            for (String col : cols) {
                head.add(Collections.singletonList(col));
            }
            // 如果有分层配置，追加“层位”列头
            long layerCount = wellLayerService.count(
                    new LambdaQueryWrapper<WellLayer>().eq(WellLayer::getFileId, fileId));
            if (layerCount > 0) {
                head.add(Collections.singletonList("层位"));
            }

	            try (ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).head(head).build()) {
	                WriteSheet writeSheet = EasyExcel.writerSheet("Filtered Data").build();
	                long totalWritten = streamWriteData(excelWriter, writeSheet, fileId, query, cols);
	                operationLogService.recordLog("报表导出", "筛选结果Excel导出", 1, totalWritten, getUserId(request));
	                log.info("[导出] Excel导出完成: fileId={}, {} 行", fileId, totalWritten);
	                try { excelWriter.finish(); } catch (Exception ignored) {
	                    log.warn("[导出] Excel流关闭失败（数据已写入）: fileId={}", fileId);
	                }
	            }
        } catch (Exception e) {
            log.error("[导出] Excel导出异常: fileId={}", fileId, e);
            response.setStatus(500);
        }
    }

    /**
     * 构建导出用的查询条件（与 pageQuery 中过滤逻辑保持一致）
     */
    private QueryWrapper<LogDataRecord> buildExportQueryWrapper(LogDataQueryDTO query) {
        QueryWrapper<LogDataRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("file_id", query.getFileId());
        if (query.getFilters() != null) {
            for (Map.Entry<String, LogDataQueryDTO.FilterRange> entry : query.getFilters().entrySet()) {
                String col = entry.getKey();
                String colLower = col.toLowerCase();
                LogDataQueryDTO.FilterRange range = entry.getValue();
                if (range == null) continue;

                // 文本列筛选：values 不为空时走 IN 条件
                if (range.getValues() != null && !range.getValues().isEmpty()) {
                    String textCol = getTextDbColumn(query.getFileId(), col);
                    if (textCol != null) {
                        wrapper.in(textCol, range.getValues());
                    }
                    continue;
                }

                // 定量列筛选
                if (range.getMin() == null && range.getMax() == null) continue;
                String dbCol = getMappedDbColumn(colLower);
                if (dbCol.equals("unmapped")) {
                    if (range.getMin() != null)
                        wrapper.apply("JSON_EXTRACT(extra_json, CONCAT('$.' ,{0})) >= {1}", col, range.getMin());
                    if (range.getMax() != null)
                        wrapper.apply("JSON_EXTRACT(extra_json, CONCAT('$.' ,{0})) <= {1}", col, range.getMax());
                } else {
                    if (range.getMin() != null) wrapper.ge(dbCol, range.getMin());
                    if (range.getMax() != null) wrapper.le(dbCol, range.getMax());
                }
            }
        }
        wrapper.orderByAsc("id");
        return wrapper;
    }

    /**
     * 根据文件 ID 获取列名列表
     */
    private List<String> getColumns(Long fileId) {
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null || fileInfo.getColumnsJson() == null) {
            return new ArrayList<>();
        }
        return JSONUtil.toList(fileInfo.getColumnsJson(), String.class);
    }

    /**
     * 流式分页写入 Excel。每轮并行预取 4 页，串行写入。
     */
    private long streamWriteData(ExcelWriter writer, WriteSheet sheet, Long fileId,
                                 LogDataQueryDTO query, List<String> cols) {
        List<WellLayer> layers = wellLayerService.list(
                new LambdaQueryWrapper<WellLayer>()
                        .eq(WellLayer::getFileId, fileId)
                        .orderByAsc(WellLayer::getTopDepth));
        boolean hasLayers = layers != null && !layers.isEmpty();

        Map<String, String> textColMapping = null;
        LogFileInfo fileInfoForText = fileInfoService.getById(fileId);
        if (fileInfoForText != null && fileInfoForText.getTextColumnsJson() != null) {
            textColMapping = JSONUtil.toBean(fileInfoForText.getTextColumnsJson(), Map.class);
        }
        final Map<String, String> finalTextColMapping = textColMapping;
        final boolean finalHasLayers = hasLayers;
        final List<WellLayer> finalLayers = layers;
        final List<String> finalCols = cols;

        final int FETCH_AHEAD = 4;
        long totalWritten = 0;
        int currentPage = 1;

        while (totalWritten < EXPORT_MAX_TOTAL) {
            List<CompletableFuture<List<List<Object>>>> futures = new ArrayList<>();
            for (int i = 0; i < FETCH_AHEAD; i++) {
                final int page = currentPage + i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    QueryWrapper<LogDataRecord> wrapper = buildExportQueryWrapper(query);
                    Page<LogDataRecord> pageParam = new Page<>(page, EXPORT_BATCH_SIZE);
                    pageParam.setSearchCount(false);
                    Page<LogDataRecord> recordPage = dataRecordService.page(pageParam, wrapper);
                    List<LogDataRecord> records = recordPage.getRecords();
                    if (records == null || records.isEmpty()) return Collections.emptyList();

                    // 文本列值补充（select=false，MyBatis-Plus 不查 text_col_N）
                    Map<Long, Map<String, String>> textVals = finalTextColMapping != null && !finalTextColMapping.isEmpty()
                            ? loadTextColumnValues(records, finalTextColMapping) : Collections.emptyMap();

                    List<List<Object>> rows = new ArrayList<>(records.size());
                    for (LogDataRecord record : records) {
                        Map<String, String> tv = textVals.getOrDefault(record.getId(), Collections.emptyMap());
                        List<Object> row = new ArrayList<>(finalCols.size() + (finalHasLayers ? 1 : 0));
                        for (String colName : finalCols) {
                            String tVal = tv.get(colName);
                            row.add(tVal != null ? tVal : readRecordValue(record, colName, finalTextColMapping));
                        }
                        if (finalHasLayers) row.add(matchLayerFromList(finalLayers, record.getDepth()));
                        rows.add(row);
                    }
                    return rows;
                }));
            }

            // 串行写入（保证顺序）
            for (CompletableFuture<List<List<Object>>> f : futures) {
                List<List<Object>> rows;
                try { rows = f.get(120, TimeUnit.SECONDS); } catch (Exception e) {
                    log.warn("[导出] 预取页失败: fileId={}", fileId, e); continue;
                }
                if (rows.isEmpty()) {
                    return totalWritten;
                }
                writer.write(rows, sheet);
                totalWritten += rows.size();
                if (rows.size() < EXPORT_BATCH_SIZE) {
                    return totalWritten;
                }
            }
            currentPage += FETCH_AHEAD;
        }

        return totalWritten;
    }

    /**
     * 从分层列表中根据深度匹配层名
     */
    private String matchLayerFromList(List<WellLayer> layers, java.math.BigDecimal depth) {
        if (depth == null || layers == null) return null;
        for (WellLayer layer : layers) {
            if (layer.getTopDepth() != null && layer.getBottomDepth() != null
                    && depth.compareTo(layer.getTopDepth()) >= 0
                    && depth.compareTo(layer.getBottomDepth()) < 0) {
                return layer.getLayerName();
            }
        }
        return null;
    }

    /**
     * 根据列名将 LogDataRecord 中的值读取出来，统一转换为 Object
     */
    private Object readRecordValue(LogDataRecord record, String colName) {
        return readRecordValue(record, colName, null);
    }

    private Object readRecordValue(LogDataRecord record, String colName, Map<String, String> textColMapping) {
        if (colName == null) return "";
        String colLower = colName.trim().toLowerCase();
        if (colLower.equals("depth") || colLower.contains("tvd") || colLower.contains("dep")) {
            return record.getDepth() != null ? record.getDepth() : "";
        }
        switch (colLower) {
            case "ac":  return record.getAc()  != null ? record.getAc()  : "";
            case "den": return record.getDen() != null ? record.getDen() : "";
            case "gr":  return record.getGr()  != null ? record.getGr()  : "";
            case "sp":  return record.getSp()  != null ? record.getSp()  : "";
            case "rt":  return record.getRt()  != null ? record.getRt()  : "";
            default: break;
        }

        // 检查是否为文本列（从 text_col_N 读取）
        if (textColMapping != null) {
            String textCol = textColMapping.get(colName);
            if (textCol == null) textCol = textColMapping.get(colName.toUpperCase());
            if (textCol == null) textCol = textColMapping.get(colLower);
            if (textCol != null) {
                String val = switch (textCol) {
                    case "text_col_1" -> record.getTextCol1();
                    case "text_col_2" -> record.getTextCol2();
                    case "text_col_3" -> record.getTextCol3();
                    case "text_col_4" -> record.getTextCol4();
                    case "text_col_5" -> record.getTextCol5();
                    case "text_col_6" -> record.getTextCol6();
                    case "text_col_7" -> record.getTextCol7();
                    case "text_col_8" -> record.getTextCol8();
                    case "text_col_9" -> record.getTextCol9();
                    case "text_col_10" -> record.getTextCol10();
                    default -> null;
                };
                if (val != null) return val;
            }
        }

        Map<String, Object> extra = record.getExtraJson();
        if (extra != null) {
            Object val = extra.get(colName);
            if (val == null) val = extra.get(colName.toUpperCase());
            if (val == null) val = extra.get(colLower);
            if (val != null) return val;
        }
        return "";
    }

    /** ECharts 降采样最大点数，超出此值将使用 LTTB 算法抽稀 */
    private static final int ECHARTS_MAX_POINTS = 5000;

    @GetMapping("/echarts/{fileId}")
    public Result<Map<String, List<Object>>> getEchartsData(@PathVariable Long fileId,
                                                              HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) return Result.failed("文件不存在");
        checkFileOwnership(fileInfo, userId);
        List<String> cols;
        if (fileInfo != null && fileInfo.getColumnsJson() != null) {
            cols = JSONUtil.toList(fileInfo.getColumnsJson(), String.class);
        } else {
            cols = new ArrayList<>();
        }

        // 1. 查询总记录数，决定加载策略
        QueryWrapper<LogDataRecord> countWrapper = new QueryWrapper<>();
        countWrapper.eq("file_id", fileId);
        long totalCount = dataRecordService.count(countWrapper);

        // 2. 初始化列式数据结构
        Map<String, List<Object>> columnData = new LinkedHashMap<>();
        for (String col : cols) {
            columnData.put(col.toUpperCase(), new ArrayList<>());
        }
        if (!columnData.containsKey("DEPTH")) {
            columnData.put("DEPTH", new ArrayList<>());
        }

        // 3. 多线程分页并行加载
        if (totalCount > 0) {
            long pageSize = Math.max(ECHARTS_MAX_POINTS, 1);
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            List<CompletableFuture<List<Map<String, Object>>>> pageFutures = new ArrayList<>();
            for (int page = 1; page <= totalPages; page++) {
                final int p = page;
                pageFutures.add(CompletableFuture.supplyAsync(() -> {
                    QueryWrapper<LogDataRecord> wrapper = new QueryWrapper<>();
                    wrapper.eq("file_id", fileId).orderByAsc("depth");
                    Page<LogDataRecord> pageParam = new Page<>(p, pageSize);
                    pageParam.setSearchCount(false);
                    Page<LogDataRecord> recordPage = dataRecordService.page(pageParam, wrapper);

                    List<Map<String, Object>> pageRows = new ArrayList<>();
                    for (LogDataRecord record : recordPage.getRecords()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("depth", record.getDepth());
                        for (String colName : cols) {
                            row.put(colName.toUpperCase(), readRecordValue(record, colName));
                        }
                        pageRows.add(row);
                    }
                    return pageRows;
                }));
            }

            // 按页码顺序合并结果
            for (int i = 0; i < pageFutures.size(); i++) {
                try {
                    List<Map<String, Object>> pageRows = pageFutures.get(i).get(120, TimeUnit.SECONDS);
                    for (Map<String, Object> row : pageRows) {
                        if (columnData.containsKey("DEPTH")) columnData.get("DEPTH").add(row.get("depth"));
                        for (String colName : cols) {
                            String key = colName.toUpperCase();
                            if (columnData.containsKey(key)) {
                                columnData.get(key).add(row.get(key));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("[Echarts] 分页 {} 加载失败", i + 1, e);
                }
            }
        }

        // 4. 超过阈值时执行 LTTB 降采样
        List<Object> depthList = columnData.get("DEPTH");
        if (depthList != null && depthList.size() > ECHARTS_MAX_POINTS) {
            List<Integer> sampledIndices = lttbSampleIndices(depthList, ECHARTS_MAX_POINTS);
            Map<String, List<Object>> sampled = new LinkedHashMap<>();
            for (Map.Entry<String, List<Object>> entry : columnData.entrySet()) {
                List<Object> src = entry.getValue();
                List<Object> dst = new ArrayList<>(sampledIndices.size());
                for (int idx : sampledIndices) {
                    dst.add(idx < src.size() ? src.get(idx) : null);
                }
                sampled.put(entry.getKey(), dst);
            }
            columnData = sampled;
        }

        return Result.success(columnData);
    }

    /**
     * LTTB (Largest Triangle Three Buckets) 降采样算法。
     * 根据深度（Y 轴）值计算最能保留曲线形状的采样索引。
     *
     * @param depthValues 深度值列表（作为 Y 轴参考）
     * @param targetPoints 目标采样点数
     * @return 选中点的原始索引列表
     */
    private List<Integer> lttbSampleIndices(List<Object> depthValues, int targetPoints) {
        int dataLength = depthValues.size();
        List<Integer> indices = new ArrayList<>(targetPoints);

        // 始终保留第一个点
        indices.add(0);

        // 将数据分成 (targetPoints - 2) 个桶，每个桶选一个最优点
        double bucketSize = (double) (dataLength - 2) / (targetPoints - 2);
        int prevSelected = 0;

        for (int i = 0; i < targetPoints - 2; i++) {
            int bucketStart = (int) Math.floor((i + 1) * bucketSize) + 1;
            int bucketEnd = Math.min((int) Math.floor((i + 2) * bucketSize) + 1, dataLength);

            // 计算下一个桶的平均值（用于三角形面积计算）
            int nextBucketStart = (int) Math.floor((i + 2) * bucketSize) + 1;
            int nextBucketEnd = Math.min((int) Math.floor((i + 3) * bucketSize) + 1, dataLength);
            if (i == targetPoints - 3) {
                nextBucketStart = dataLength - 1;
                nextBucketEnd = dataLength;
            }

            double avgDepth = 0;
            int nextCount = 0;
            for (int j = nextBucketStart; j < nextBucketEnd; j++) {
                double d = toDouble(depthValues.get(j));
                if (Double.isFinite(d)) {
                    avgDepth += d;
                    nextCount++;
                }
            }
            if (nextCount > 0) avgDepth /= nextCount;

            // 在当前桶中找到与上一个选中点和下一个桶平均值形成最大三角形的点
            double prevDepth = toDouble(depthValues.get(prevSelected));
            double maxArea = -1;
            int bestIdx = bucketStart;

            for (int j = bucketStart; j < bucketEnd; j++) {
                double d = toDouble(depthValues.get(j));
                // 三角形面积 = 0.5 * |x0(y1-y2) + x1(y2-y0) + x2(y0-y1)|
                // 用索引作为 X 轴，深度值作为 Y 轴
                double area = Math.abs((prevSelected - avgDepth) * (d - prevDepth)
                        - (prevSelected - j) * (avgDepth - prevDepth)) * 0.5;
                if (area > maxArea) {
                    maxArea = area;
                    bestIdx = j;
                }
            }

            indices.add(bestIdx);
            prevSelected = bestIdx;
        }

        // 始终保留最后一个点
        indices.add(dataLength - 1);

        return indices;
    }

    /**
     * 将 Object 安全转为 double，失败返回 NaN
     */
    private double toDouble(Object value) {
        if (value == null) return Double.NaN;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private String readColumnValue(LogDataRecord row, String col) {
        // 先查物理列，再查 extraJson
        switch (col) {
            case "depth": case "DEPTH": return row.getDepth() != null ? row.getDepth().toString() : null;
            case "ac": case "AC": return row.getAc() != null ? row.getAc().toString() : null;
            case "den": case "DEN": return row.getDen() != null ? row.getDen().toString() : null;
            case "gr": case "GR": return row.getGr() != null ? row.getGr().toString() : null;
            case "rt": case "RT": return row.getRt() != null ? row.getRt().toString() : null;
            case "sp": case "SP": return row.getSp() != null ? row.getSp().toString() : null;
        }
        // 查 extraJson
        if (row.getExtraJson() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> extra = (Map<String, Object>) row.getExtraJson();
            Object v = extra.get(col);
            return v != null ? String.valueOf(v) : null;
        }
        return null;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
