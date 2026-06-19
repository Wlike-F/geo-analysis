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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

    /** 每批导出行数（受限于 MybatisPlus 分页上限 10000） */
    private static final int EXPORT_BATCH_SIZE = 10000;
    /** 单次导出最大行数（xlsx 格式上限 1,048,576，留余量给表头） */
    private static final int EXPORT_MAX_TOTAL = 1000000;

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
        return 1L;
    }

    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> pageQuery(@RequestBody LogDataQueryDTO query) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null || query.getSize() < 1 ? 100L : Math.min(query.getSize(), 10000L);
        Page<LogDataRecord> pageParam = new Page<>(current, size);
        QueryWrapper<LogDataRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("file_id", query.getFileId());

        if (query.getFilters() != null) {
            for (Map.Entry<String, LogDataQueryDTO.FilterRange> entry : query.getFilters().entrySet()) {
                String col = entry.getKey();
                String colLower = col.toLowerCase();
                LogDataQueryDTO.FilterRange range = entry.getValue();
                if (range == null || (range.getMin() == null && range.getMax() == null)) continue;

                String dbCol = getMappedDbColumn(colLower);
                if (dbCol.equals("unmapped")) {
                    if (range.getMin() != null) wrapper.apply("JSON_EXTRACT(extra_json, '$." + col + "') >= {0}", range.getMin());
                    if (range.getMax() != null) wrapper.apply("JSON_EXTRACT(extra_json, '$." + col + "') <= {0}", range.getMax());
                } else {
                    if (range.getMin() != null) wrapper.ge(dbCol, range.getMin());
                    if (range.getMax() != null) wrapper.le(dbCol, range.getMax());
                }
            }
        }

        wrapper.orderByAsc("id");
        Page<LogDataRecord> recordPage = dataRecordService.page(pageParam, wrapper);

        LogFileInfo fileInfo = fileInfoService.getById(query.getFileId());
        List<String> cols = new ArrayList<>();
        if (fileInfo != null && fileInfo.getColumnsJson() != null) {
            cols = JSONUtil.toList(fileInfo.getColumnsJson(), String.class);
        }

        List<Map<String, Object>> mapList = new ArrayList<>();
        for (LogDataRecord record : recordPage.getRecords()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", record.getId());

            for (String colName : cols) {
                String colLower = colName.toLowerCase();
                if (colLower.equals("depth") || colLower.contains("tvd") || colLower.contains("dep")) {
                    map.put(colName, record.getDepth() != null ? record.getDepth() : "");
                } else if (colLower.equals("ac")) {
                    map.put(colName, record.getAc() != null ? record.getAc() : "");
                } else if (colLower.equals("den")) {
                    map.put(colName, record.getDen() != null ? record.getDen() : "");
                } else if (colLower.equals("gr")) {
                    map.put(colName, record.getGr() != null ? record.getGr() : "");
                } else if (colLower.equals("sp")) {
                    map.put(colName, record.getSp() != null ? record.getSp() : "");
                } else if (colLower.equals("rt")) {
                    map.put(colName, record.getRt() != null ? record.getRt() : "");
                }
            }

            if (record.getExtraJson() != null && !record.getExtraJson().isEmpty()) {
                map.putAll(record.getExtraJson());
            }
            mapList.add(map);
        }

        Page<Map<String, Object>> resultPage = new Page<>(query.getCurrent(), query.getSize(), recordPage.getTotal());
        resultPage.setRecords(mapList);

        return Result.success(resultPage);
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

    @PostMapping("/export-batch-zip")
    public void exportBatchZip(@RequestBody List<LogDataQueryDTO> queries, HttpServletRequest request, HttpServletResponse response) {
        try {
            int exportedFileCount = 0;
            long exportedLineCount = 0L;
            Long userId = getUserId(request);

            response.setContentType("application/zip");
            response.setCharacterEncoding("utf-8");
            String zipName = URLEncoder.encode("批量测井数据导出", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + zipName + ".zip");

            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
                for (LogDataQueryDTO query : queries) {
                    if (query.getFileId() == null) continue;

                    LogFileInfo fileInfo = fileInfoService.getById(query.getFileId());
                    if (fileInfo == null) continue;

                    List<String> cols = getColumns(query.getFileId());
                    if (cols.isEmpty()) continue;

                    String fileName = fileInfo.getFileName() != null ? fileInfo.getFileName() : ("File_" + query.getFileId() + ".txt");
                    fileName = fileName.replaceAll("[\\\\/?*:\\[\\]]", "_") + ".xlsx";

                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(fileName);
                    zos.putNextEntry(zipEntry);

                    List<List<String>> head = new ArrayList<>();
                    for (String col : cols) {
                        head.add(Collections.singletonList(col));
                    }
                    // 如果有分层配置，追加“层位”列头
                    long layerCount = wellLayerService.count(
                            new LambdaQueryWrapper<WellLayer>().eq(WellLayer::getFileId, query.getFileId()));
                    if (layerCount > 0) {
                        head.add(Collections.singletonList("层位"));
                    }
                    ExcelWriter excelWriter = EasyExcel.write(zos).head(head).autoCloseStream(Boolean.FALSE).build();
                    WriteSheet writeSheet = EasyExcel.writerSheet("Filtered Data").build();

                    long fileLineCount = streamWriteData(excelWriter, writeSheet, query.getFileId(), query, cols);
                    excelWriter.finish();

                    zos.closeEntry();
                    exportedFileCount++;
                    exportedLineCount += fileLineCount;
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

    @PostMapping("/{fileId}/export")
    public void exportExcel(@PathVariable Long fileId, @RequestBody(required = false) LogDataQueryDTO query, HttpServletRequest request, HttpServletResponse response) {
        if (query == null) query = new LogDataQueryDTO();
        query.setFileId(fileId);

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
                if (range == null || (range.getMin() == null && range.getMax() == null)) continue;
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
     * 流式分页写入 Excel，避免一次性加载全部数据到内存。
     * 每页读取 EXPORT_BATCH_SIZE 条记录并立即写入 ExcelWriter，
     * 累计最多写入 EXPORT_MAX_TOTAL 行。
     *
     * @return 实际写入的总行数
     */
    private long streamWriteData(ExcelWriter writer, WriteSheet sheet, Long fileId,
                                 LogDataQueryDTO query, List<String> cols) {
        // 一次性加载该文件的分层配置
        List<WellLayer> layers = wellLayerService.list(
                new LambdaQueryWrapper<WellLayer>()
                        .eq(WellLayer::getFileId, fileId)
                        .orderByAsc(WellLayer::getTopDepth));
        boolean hasLayers = layers != null && !layers.isEmpty();

        long totalWritten = 0;
        int currentPage = 1;

        while (totalWritten < EXPORT_MAX_TOTAL) {
            QueryWrapper<LogDataRecord> wrapper = buildExportQueryWrapper(query);
            Page<LogDataRecord> pageParam = new Page<>(currentPage, EXPORT_BATCH_SIZE);
            // 关闭 count 查询以提升性能
            pageParam.setSearchCount(false);
            Page<LogDataRecord> recordPage = dataRecordService.page(pageParam, wrapper);
            List<LogDataRecord> records = recordPage.getRecords();

            if (records == null || records.isEmpty()) break;

            List<List<Object>> rows = new ArrayList<>(records.size());
            for (LogDataRecord record : records) {
                List<Object> row = new ArrayList<>(cols.size() + (hasLayers ? 1 : 0));
                for (String colName : cols) {
                    row.add(readRecordValue(record, colName));
                }
                // 如果有分层配置，根据 depth 匹配层名
                if (hasLayers) {
                    String layerName = matchLayerFromList(layers, record.getDepth());
                    row.add(layerName != null ? layerName : "");
                }
                rows.add(row);
            }

            writer.write(rows, sheet);
            totalWritten += records.size();

            if (records.size() < EXPORT_BATCH_SIZE) break;
            currentPage++;
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
    public Result<Map<String, List<Object>>> getEchartsData(@PathVariable Long fileId) {
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        List<String> cols = new ArrayList<>();
        if (fileInfo != null && fileInfo.getColumnsJson() != null) {
            cols = JSONUtil.toList(fileInfo.getColumnsJson(), String.class);
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

        // 3. 分页加载数据（每页 ECHARTS_MAX_POINTS 条，内存恒定）
        if (totalCount > 0) {
            long pageSize = Math.max(ECHARTS_MAX_POINTS, 1);
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);

            for (int page = 1; page <= totalPages; page++) {
                QueryWrapper<LogDataRecord> wrapper = new QueryWrapper<>();
                wrapper.eq("file_id", fileId).orderByAsc("depth");
                Page<LogDataRecord> pageParam = new Page<>(page, pageSize);
                pageParam.setSearchCount(false);
                Page<LogDataRecord> recordPage = dataRecordService.page(pageParam, wrapper);

                for (LogDataRecord record : recordPage.getRecords()) {
                    if (columnData.containsKey("DEPTH")) columnData.get("DEPTH").add(record.getDepth());
                    for (String colName : cols) {
                        columnData.get(colName.toUpperCase()).add(readRecordValue(record, colName));
                    }
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
}
