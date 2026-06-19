package com.xy.welllog.controller;

import cn.hutool.core.io.FileUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xy.welllog.common.Result;
import com.xy.welllog.dto.WellLogDataDTO;
import com.xy.welllog.dto.WellLogFileDTO;
import com.xy.welllog.dto.PreviewResultDTO;
import com.xy.welllog.dto.ConfirmUploadDTO;
import com.xy.welllog.entity.LogDataRecord;
import com.xy.welllog.entity.LogFileInfo;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.entity.WellLayer;
import com.xy.welllog.service.*;
import com.xy.welllog.utils.JwtUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final SysOperationLogService operationLogService;
    private final SysUserService sysUserService;
    private final LogFileInfoService fileInfoService;
    private final LogDataRecordService dataRecordService;
    private final JwtUtils jwtUtils;
    private final SysColumnMappingService mappingService;
    private final LogFileParseService logFileParseService;
    private final WellLayerService wellLayerService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.upload.dirs}")
    private List<String> uploadDirs;

    /** 启动时解析出第一个可用的上传目录 */
    private String resolvedUploadDir;

    @PostConstruct
    public void initUploadDir() {
        for (String dir : uploadDirs) {
            if (FileUtil.exist(dir) || FileUtil.mkdir(dir) != null) {
                resolvedUploadDir = dir;
                log.info("上传目录已就绪: {}", dir);
                return;
            }
            log.warn("上传目录不可用，尝试下一个: {}", dir);
        }
        log.error("所有配置的上传目录均不可用: {}", uploadDirs);
    }

    /** 获取可用上传目录，运行时动态检查（防止运行中目录失效） */
    private String getUploadDir() {
        if (resolvedUploadDir != null && FileUtil.exist(resolvedUploadDir)) {
            return resolvedUploadDir;
        }
        // 运行时主目录失效，重新扫描
        initUploadDir();
        return resolvedUploadDir;
    }

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

    /**
     * P2 Sandbox: 上传文件进行预解析，不直接入库
     */
    @PostMapping("/preview")
    public Result<PreviewResultDTO> previewFile(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (file.isEmpty()) {
            return Result.failed("文件内容为空");
        }
        try {
            String uploadDir = getUploadDir();
            if (uploadDir == null) {
                return Result.failed("文件上传目录不可用，请检查配置");
            }

            String originalFileName = file.getOriginalFilename();
            String tempName = UUID.randomUUID() + "_" + originalFileName;
            File destTempFile = new File(uploadDir, tempName);
            file.transferTo(destTempFile);

            // 调用同步预览服务
            PreviewResultDTO dto = logFileParseService.previewTxtStreamSync(destTempFile, originalFileName);
            dto.setTempFilePath(destTempFile.getAbsolutePath());
            dto.setOriginalFileName(originalFileName);

            operationLogService.recordLog("文件预载", "预览测井档案 [" + originalFileName + "]", 1, 0L, getUserId(request));
            
            return Result.success(dto, "预解析成功");
        } catch (Exception e) {
            log.error("文件预览异常: ", e);
            return Result.failed("系统内部预览出错");
        }
    }

    /**
     * P2 Sandbox: 确认上传，应用最终映射关系并异步入库
     */
    @PostMapping("/confirm")
    public Result<String> confirmUpload(@RequestBody ConfirmUploadDTO confirmDto, HttpServletRequest request) {
        File file = new File(confirmDto.getTempFilePath());
        if (!file.exists()) {
            return Result.failed("临时文件已过期或不存在");
        }

        try {
            LogFileInfo fileInfo = new LogFileInfo();
            fileInfo.setUserId(getUserId(request));
            fileInfo.setFileName(confirmDto.getOriginalFileName());
            fileInfo.setTotalRows(0);
            fileInfo.setStatus(0); // 解析中
            fileInfo.setCreateTime(new Date());
            fileInfoService.save(fileInfo);
            
            Long fileId = fileInfo.getId();

            // 投递到线程池，执行最终解析
            logFileParseService.asyncParseAndSaveTxtStream(file, confirmDto.getOriginalFileName(), fileId, confirmDto.getConfirmedMapping());

            operationLogService.recordLog("确认上传", "正式解析测井档案 [" + confirmDto.getOriginalFileName() + "]", 1, 0L, getUserId(request));
            
            return Result.success("任务已提交，系统正在后台极速处理");
        } catch (Exception e) {
            log.error("确认上传异常: ", e);
            return Result.failed("提交解析任务失败");
        }
    }

    /**
     * 读取服务器某个绝对路径下所有的 TXT 测井文件并批量入库（自动应用建议映射）
     */
    @GetMapping("/scan")
    public Result<String> scanServerPath(@RequestParam("path") String path, HttpServletRequest request) {
        if (!FileUtil.isDirectory(path)) {
            return Result.failed("该路径不存在或不是一个目录");
        }

        List<File> txtFiles = FileUtil.loopFiles(path, pathname -> pathname.getName().toLowerCase().matches(".*\\.(txt|csv|xls|xlsx)$"));
        if (txtFiles == null || txtFiles.isEmpty()) {
            return Result.failed("指定目录下没有任何受支持格式(.txt/.csv/.xls/.xlsx)的测井记录");
        }

        int submittedCount = 0;
        int skippedCount = 0;
        for (File txtFile : txtFiles) {
            try {
                long exists = fileInfoService.count(new LambdaQueryWrapper<LogFileInfo>()
                        .eq(LogFileInfo::getFileName, txtFile.getName()));
                if (exists > 0) {
                    skippedCount++;
                    continue;
                }

                // 扫描模式下，先同步获取建议映射
                PreviewResultDTO preview = logFileParseService.previewTxtStreamSync(txtFile, txtFile.getName());
                
                LogFileInfo fileInfo = new LogFileInfo();
                fileInfo.setUserId(getUserId(request));
                fileInfo.setFileName(txtFile.getName());
                fileInfo.setStatus(0);
                fileInfo.setCreateTime(new Date());
                fileInfoService.save(fileInfo);
                
                logFileParseService.asyncParseAndSaveTxtStream(txtFile, txtFile.getName(), fileInfo.getId(), preview.getSuggestedMapping());
                submittedCount++;
            } catch (Exception e) {
                log.warn("跳过不合规的文件: {}", txtFile.getName(), e);
            }
        }
        
        operationLogService.recordLog("文件扫描", "批量扫描目录 [" + path + "] 提交 " + submittedCount + " 个任务", submittedCount, 0L, getUserId(request));
        return Result.success("扫描完毕，已在后台极速处理");
    }

    @PostMapping("/export")
    public void exportToExcel(@RequestBody List<WellLogDataDTO> exportData, HttpServletResponse response) throws IOException {
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("地质测井数据归档报表", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
            
            EasyExcel.write(response.getOutputStream(), WellLogDataDTO.class)
                    .sheet("筛选归档数据")
                    .doWrite(exportData);
                    
            operationLogService.recordLog("报表导出", "单项数据归档导出", 1, (long) exportData.size(), getUserId(request));
        } catch (Exception e) {
            log.error("Excel 写入异常", e);
        }
    }

    @PostMapping("/export-batch")
    public void exportBatchToExcel(@RequestBody List<WellLogFileDTO> exportDataList, HttpServletResponse response) throws IOException {
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("一键批量测井数据报表", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
            
            try (ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream(), WellLogDataDTO.class).build()) {
                for (int i = 0; i < exportDataList.size(); i++) {
                    WellLogFileDTO fileDTO = exportDataList.get(i);
                    String sheetName = fileDTO.getTitle().replaceAll("[\\\\/?*:\\[\\]]", "_");
                    if (sheetName.length() > 31) sheetName = sheetName.substring(0, 31);
                    WriteSheet writeSheet = EasyExcel.writerSheet(i, sheetName.isEmpty() ? "Sheet" + (i + 1) : sheetName).build();
                    excelWriter.write(fileDTO.getData(), writeSheet);
                }
            }
            operationLogService.recordLog("报表导出", "批量多Sheet报表导出", exportDataList.size(), null, getUserId(request));
        } catch (Exception e) {
            log.error("批量导出Excel异常", e);
        }
    }

    @PostMapping("/export-batch-zip")
    public void exportBatchToZip(@RequestBody List<WellLogFileDTO> exportDataList, HttpServletResponse response) throws IOException {
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        try {
            response.setContentType("application/zip");
            response.setCharacterEncoding("utf-8");
            String zipFileName = URLEncoder.encode("测井数据报表集", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + zipFileName + ".zip");

            try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
                for (WellLogFileDTO fileDTO : exportDataList) {
                    String excelFileName = fileDTO.getTitle();
                    int lastDotIndex = excelFileName.lastIndexOf(".");
                    if (lastDotIndex > 0) {
                        excelFileName = excelFileName.substring(0, lastDotIndex);
                    }
                    excelFileName += ".xlsx";
                    zos.putNextEntry(new ZipEntry(excelFileName));

                    List<List<String>> head = new ArrayList<>();
                    if (fileDTO.getColumns() != null) {
                        for (String col : fileDTO.getColumns()) head.add(Collections.singletonList(col));
                    }
                    List<List<Object>> dataList = new ArrayList<>();
                    if (fileDTO.getData() != null) {
                        for (Map<String, Object> map : fileDTO.getData()) {
                            List<Object> row = new ArrayList<>();
                            if (fileDTO.getColumns() != null) {
                                for (String col : fileDTO.getColumns()) row.add(map.getOrDefault(col, ""));
                            }
                            dataList.add(row);
                        }
                    }

                    EasyExcel.write(zos).head(head).autoCloseStream(Boolean.FALSE).sheet("数据").doWrite(dataList);
                    zos.closeEntry();
                }
                zos.finish();
            }
            operationLogService.recordLog("报表导出", "批量ZIP导出", exportDataList.size(), null, getUserId(request));
        } catch (Exception e) {
            log.error("批量导出ZIP异常", e);
        }
    }

    @GetMapping("/list")
    public Result<List<LogFileInfo>> getFileList(HttpServletRequest request) {
        LambdaQueryWrapper<LogFileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(LogFileInfo::getStatus, Arrays.asList(0, 1, -1))
               .eq(LogFileInfo::getUserId, getUserId(request))
               .orderByDesc(LogFileInfo::getCreateTime);
        return Result.success(fileInfoService.list(wrapper));
    }

    @GetMapping("/page")
    public Result<Page<LogFileInfo>> getFilePage(@RequestParam(defaultValue = "1") Integer current,
                                                 @RequestParam(defaultValue = "10") Integer size,
                                                 @RequestParam(required = false) String fileName,
                                                 HttpServletRequest request) {
        Page<LogFileInfo> page = new Page<>(current, size);
        LambdaQueryWrapper<LogFileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(LogFileInfo::getStatus, Arrays.asList(0, 1, -1))
                .eq(LogFileInfo::getUserId, getUserId(request));
        if (StringUtils.hasText(fileName)) wrapper.like(LogFileInfo::getFileName, fileName);
        wrapper.orderByDesc(LogFileInfo::getCreateTime);
        return Result.success(fileInfoService.page(page, wrapper));
    }

    @GetMapping("/{id}/parse-report")
    public Result<Map<String, Object>> getParseReport(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(id);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            return Result.failed("文件不存在或无权查看");
        }

        List<String> columns = new ArrayList<>();
        if (StringUtils.hasText(fileInfo.getColumnsJson())) {
            columns = cn.hutool.json.JSONUtil.toList(fileInfo.getColumnsJson(), String.class);
        }

        // --- 用 SQL 聚合代替全量加载 ---

        // 1. 总记录数
        Long recordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM log_data_records WHERE file_id = ?", Long.class, id);
        recordCount = recordCount != null ? recordCount : 0L;

        // 2. 核心列聚合统计（排除哨兵值 -9999, -999.25, -999）
        String sentinelFilter = "(col IS NOT NULL AND col != -9999 AND col != -999.25 AND col != -999)";
        Map<String, Map<String, Object>> sqlStats = new LinkedHashMap<>();
        String[] coreCols = {"depth", "ac", "den", "gr", "sp", "rt"};

        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> selectParts = new ArrayList<>();
        for (String col : coreCols) {
            String cond = sentinelFilter.replace("col", col);
            selectParts.add("SUM(CASE WHEN " + cond + " THEN 1 ELSE 0 END) AS " + col + "_valid");
            selectParts.add("SUM(CASE WHEN " + cond + " THEN 0 ELSE 1 END) AS " + col + "_invalid");
            selectParts.add("MIN(CASE WHEN " + cond + " THEN " + col + " END) AS " + col + "_min");
            selectParts.add("MAX(CASE WHEN " + cond + " THEN " + col + " END) AS " + col + "_max");
        }
        sql.append(String.join(", ", selectParts));
        sql.append(" FROM log_data_records WHERE file_id = ?");

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql.toString(), id);
            for (String col : coreCols) {
                Map<String, Object> stat = new HashMap<>();
                stat.put("valid", toLong(row.get(col + "_valid")));
                stat.put("invalid", toLong(row.get(col + "_invalid")));
                stat.put("min", toDouble(row.get(col + "_min")));
                stat.put("max", toDouble(row.get(col + "_max")));
                sqlStats.put(col, stat);
            }
        } catch (Exception e) {
            log.warn("解析报告SQL聚合查询异常", e);
        }

        // 3. 构建列统计结果
        Map<String, ColumnReport> statsMap = new LinkedHashMap<>();
        int totalInvalidCount = 0;
        Double depthMin = null;
        Double depthMax = null;

        for (String col : columns) {
            if (isWellNameColumn(col)) continue;
            ColumnReport report = new ColumnReport(col);
            String mappedCol = getMappedCoreColumn(col);

            if (mappedCol != null && sqlStats.containsKey(mappedCol)) {
                Map<String, Object> stat = sqlStats.get(mappedCol);
                report.validCount = toLong(stat.get("valid"));
                report.invalidCount = toLong(stat.get("invalid"));
                report.min = toDouble(stat.get("min"));
                report.max = toDouble(stat.get("max"));
                totalInvalidCount += report.invalidCount;

                if (isDepthColumn(col)) {
                    depthMin = report.min;
                    depthMax = report.max;
                }
            }
            statsMap.put(col, report);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId", fileInfo.getId());
        result.put("fileName", fileInfo.getFileName());
        result.put("status", fileInfo.getStatus());
        result.put("totalRows", fileInfo.getTotalRows());
        result.put("recordCount", recordCount);
        result.put("columns", columns);
        result.put("columnCount", columns.size());
        result.put("depthMin", depthMin);
        result.put("depthMax", depthMax);
        result.put("invalidValueCount", totalInvalidCount);
        result.put("dirtyLineCount", countDirtyLines(id));
        result.put("columnStats", new ArrayList<>(statsMap.values()));

        return Result.success(result);
    }

    /** 将列名映射到核心数据库列名，非核心列返回 null */
    private String getMappedCoreColumn(String colName) {
        if (colName == null) return null;
        String lower = colName.trim().toLowerCase();
        if (lower.equals("depth") || lower.contains("tvd") || lower.contains("dep") || lower.contains("深")) return "depth";
        switch (lower) {
            case "ac": return "ac";
            case "den": return "den";
            case "gr": return "gr";
            case "sp": return "sp";
            case "rt": return "rt";
            default: return null;
        }
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(String.valueOf(val)); } catch (Exception e) { return 0L; }
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); } catch (Exception e) { return null; }
    }

    private Object readRecordValue(LogDataRecord record, String colName) {
        String colLower = colName == null ? "" : colName.trim().toLowerCase();
        if (isDepthColumn(colName)) return record.getDepth();
        return switch (colLower) {
            case "ac" -> record.getAc();
            case "den" -> record.getDen();
            case "gr" -> record.getGr();
            case "sp" -> record.getSp();
            case "rt" -> record.getRt();
            default -> readExtraValue(record.getExtraJson(), colName);
        };
    }

    private Object readExtraValue(Map<String, Object> extra, String colName) {
        if (extra == null || colName == null) return null;
        Object value = extra.get(colName);
        if (value != null) return value;
        String upper = colName.toUpperCase();
        value = extra.get(upper);
        if (value != null) return value;
        String lower = colName.toLowerCase();
        value = extra.get(lower);
        if (value != null) return value;
        for (Map.Entry<String, Object> entry : extra.entrySet()) {
            if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(colName.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isDepthColumn(String colName) {
        if (colName == null) return false;
        String colLower = colName.trim().toLowerCase();
        return colLower.equals("depth") || colLower.contains("tvd") || colLower.contains("dep") || colLower.contains("深");
    }

    private boolean isWellNameColumn(String colName) {
        if (colName == null) return false;
        String normalized = colName.trim().toLowerCase();
        return normalized.equals("井号")
                || normalized.equals("岩性")
                || normalized.equals("well")
                || normalized.equals("well_name")
                || normalized.equals("wellname")
                || normalized.equals("well no")
                || normalized.equals("well_no");
    }

    private Double toValidReportNumber(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return isInvalidSentinel(decimal.doubleValue()) ? null : decimal.doubleValue();
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text) || "nan".equalsIgnoreCase(text) || "null".equalsIgnoreCase(text)) return null;
        try {
            double number = Double.parseDouble(text);
            return isInvalidSentinel(number) ? null : number;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isInvalidSentinel(double value) {
        return !Double.isFinite(value)
                || Math.abs(value + 9999d) < 0.0000001d
                || Math.abs(value + 999.25d) < 0.0000001d
                || Math.abs(value + 999d) < 0.0000001d;
    }

    private long countDirtyLines(Long fileId) {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM log_dirty_data WHERE file_id = ?", Long.class, fileId);
            return count == null ? 0L : count;
        } catch (Exception e) {
            return 0L;
        }
    }

    @lombok.Data
    private static class ColumnReport {
        private final String column;
        private long validCount;
        private long invalidCount;
        private Double min;
        private Double max;
    }

    @DeleteMapping("/clear")
    public Result<String> clearFiles(HttpServletRequest request) {
        Long userId = getUserId(request);
        LambdaQueryWrapper<LogFileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogFileInfo::getUserId, userId)
               .ne(LogFileInfo::getStatus, 2); // 排除已删除的
        
        List<LogFileInfo> files = fileInfoService.list(wrapper);
        if (files == null || files.isEmpty()) {
            return Result.success("记录为空，无需清理");
        }
        
        files.forEach(f -> f.setStatus(2));
        fileInfoService.updateBatchById(files);
        
        operationLogService.recordLog("文件管理", "一键大扫除", files.size(), null, userId);
        return Result.success("所有记录全部清空完成");
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteFile(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(id);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            return Result.failed("文件不存在或无权删除");
        }
        fileInfo.setStatus(2);
        fileInfoService.updateById(fileInfo);
        operationLogService.recordLog("文件管理", "删除文件 [" + fileInfo.getFileName() + "]", 1, null, userId);
        return Result.success("删除成功");
    }

    // ==================== 地质分层配置 ====================

    @GetMapping("/{id}/layers")
    public Result<List<WellLayer>> getLayers(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(id);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            return Result.failed("文件不存在或无权访问");
        }
        LambdaQueryWrapper<WellLayer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WellLayer::getFileId, id)
               .orderByAsc(WellLayer::getTopDepth);
        return Result.success(wellLayerService.list(wrapper));
    }

    @PostMapping("/{id}/layers")
    public Result<String> saveLayers(@PathVariable Long id, @RequestBody List<WellLayer> layers, HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(id);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            return Result.failed("文件不存在或无权操作");
        }
        // 全量覆盖：先删后插
        LambdaQueryWrapper<WellLayer> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(WellLayer::getFileId, id);
        wellLayerService.remove(deleteWrapper);

        if (layers != null && !layers.isEmpty()) {
            layers.removeIf(java.util.Objects::isNull);
            for (WellLayer layer : layers) {
                layer.setId(null);
                layer.setFileId(id);
                layer.setCreateTime(new Date());
            }
            if (!layers.isEmpty()) {
                wellLayerService.saveBatch(layers);
            }
        }
        operationLogService.recordLog("分层配置", "保存分层配置 [" + fileInfo.getFileName() + "] " + (layers != null ? layers.size() : 0) + " 层", 1, null, userId);
        return Result.success("分层配置保存成功");
    }

    @DeleteMapping("/{id}/layers")
    public Result<String> deleteLayers(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(id);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            return Result.failed("文件不存在或无权操作");
        }
        LambdaQueryWrapper<WellLayer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WellLayer::getFileId, id);
        wellLayerService.remove(wrapper);
        return Result.success("分层配置已清空");
    }

    // ==================== 跨文件复制分层 ====================

    @PostMapping("/{id}/layers/copy")
    public Result<String> copyLayersToFiles(@PathVariable Long id,
                                             @RequestBody List<Long> targetFileIds,
                                             HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo sourceFile = fileInfoService.getById(id);
        if (sourceFile == null || !sourceFile.getUserId().equals(userId)) {
            return Result.failed("源文件不存在或无权操作");
        }
        if (targetFileIds == null || targetFileIds.isEmpty()) {
            return Result.failed("请选择目标文件");
        }

        // 获取源文件的分层配置
        List<WellLayer> sourceLayers = wellLayerService.list(
                new LambdaQueryWrapper<WellLayer>()
                        .eq(WellLayer::getFileId, id)
                        .orderByAsc(WellLayer::getTopDepth));
        if (sourceLayers == null || sourceLayers.isEmpty()) {
            return Result.failed("源文件没有分层配置可复制");
        }

        int copiedCount = 0;
        for (Long targetId : targetFileIds) {
            if (targetId.equals(id)) continue;
            LogFileInfo targetFile = fileInfoService.getById(targetId);
            if (targetFile == null || !targetFile.getUserId().equals(userId)) continue;

            // 先删除目标文件已有的分层
            wellLayerService.remove(new LambdaQueryWrapper<WellLayer>()
                    .eq(WellLayer::getFileId, targetId));

            // 复制源文件分层到目标文件
            List<WellLayer> copies = new ArrayList<>();
            for (WellLayer src : sourceLayers) {
                WellLayer copy = new WellLayer();
                copy.setFileId(targetId);
                copy.setLayerName(src.getLayerName());
                copy.setTopDepth(src.getTopDepth());
                copy.setBottomDepth(src.getBottomDepth());
                copy.setRemark(src.getRemark());
                copy.setCreateTime(new Date());
                copies.add(copy);
            }
            wellLayerService.saveBatch(copies);
            copiedCount++;
        }

        operationLogService.recordLog("分层配置", "复制分层 [" + sourceFile.getFileName() + "] 到 " + copiedCount + " 个文件", copiedCount, null, userId);
        return Result.success("已复制分层配置到 " + copiedCount + " 个文件");
    }

    // ==================== 文件导入分层（支持 Excel/CSV/TXT） ====================

    @PostMapping("/{id}/layers/import")
    public Result<?> importLayersFromExcel(@PathVariable Long id,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "wellName", required = false) String selectedWellName,
                                             HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(id);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            return Result.failed("文件不存在或无权操作");
        }

        if (file.isEmpty()) {
            return Result.failed("文件内容为空");
        }

        try {
            // 根据文件类型选择不同的读取方式
            String originalFilename = file.getOriginalFilename();
            String ext = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase() : "xlsx";

            List<Map<Integer, String>> rows;
            if (ext.equals("txt") || ext.equals("csv")) {
                // TXT/CSV文件：手动解析（检测编码 + 自动检测分隔符）
                rows = parseTxtToRows(file);
            } else {
                // Excel文件：用EasyExcel默认读取
                rows = com.alibaba.excel.EasyExcel.read(file.getInputStream())
                        .sheet().headRowNumber(0).doReadSync();
            }

            if (rows == null || rows.isEmpty()) {
                return Result.failed("文件内容为空");
            }

            // 智能识别列位置：从第一行（表头）中查找层名/顶深/底深/井名列
            Map<Integer, String> headerRow = rows.get(0);
            int layerColIdx = -1;
            int topColIdx = -1;
            int bottomColIdx = -1;
            int remarkColIdx = -1;
            int wellNameColIdx = -1;

            for (Map.Entry<Integer, String> entry : headerRow.entrySet()) {
                String headerVal = entry.getValue() != null ? entry.getValue().trim().toLowerCase() : "";
                int idx = entry.getKey();

                // 井名列：包含"井"或"well"
                if ((headerVal.contains("井") || headerVal.contains("well")) && wellNameColIdx == -1) {
                    wellNameColIdx = idx;
                }
                // 层位列：包含"层"字或"layer"
                else if ((headerVal.contains("层") || headerVal.contains("layer")) && layerColIdx == -1) {
                    layerColIdx = idx;
                }
                // 顶深列：包含"顶"或"top"
                else if ((headerVal.contains("顶") || headerVal.contains("top")) && topColIdx == -1) {
                    topColIdx = idx;
                }
                // 底深列：包含"底"或"bottom"
                else if ((headerVal.contains("底") || headerVal.contains("bottom")) && bottomColIdx == -1) {
                    bottomColIdx = idx;
                }
                // 备注列：包含"备注"或"remark"
                else if ((headerVal.contains("备注") || headerVal.contains("remark")) && remarkColIdx == -1) {
                    remarkColIdx = idx;
                }
            }

            // 如果没识别到关键列，尝试简单格式（3列：层名/顶深/底深）
            if (layerColIdx == -1 && topColIdx == -1 && bottomColIdx == -1) {
                if (headerRow.size() >= 3) {
                    layerColIdx = 0;
                    topColIdx = 1;
                    bottomColIdx = 2;
                    if (headerRow.size() > 3) remarkColIdx = 3;
                    log.info("Excel未识别到表头关键词，使用简单3列格式：层名/顶深/底深");
                } else {
                    return Result.failed("无法识别Excel列结构，请确保表头包含'层'、'顶'、'底'关键词，或使用3列格式（层名/顶深/底深）");
                }
            }

            if (layerColIdx == -1 || topColIdx == -1 || bottomColIdx == -1) {
                return Result.failed("未能识别完整列结构，需要层名列、顶深列、底深列");
            }

            log.info("Excel列识别结果：井名=列{}, 层名=列{}, 顶深=列{}, 底深=列{}, 备注=列{}",
                    wellNameColIdx, layerColIdx, topColIdx, bottomColIdx, remarkColIdx);

            // 井名预筛选：如果识别到井名列，收集所有井名并尝试自动匹配
            String filterWellName = null;
            if (wellNameColIdx >= 0) {
                Set<String> wellNames = new LinkedHashSet<>();
                for (int i = 1; i < rows.size(); i++) {
                    Map<Integer, String> row = rows.get(i);
                    String wn = getStringValue(row, wellNameColIdx);
                    if (wn != null && !wn.isEmpty()) wellNames.add(wn.trim());
                }

                if (wellNames.size() > 1) {
                    // 多个井名，尝试用当前文件名自动匹配
                    String currentFileName = fileInfo.getFileName();
                    String fileNameNoExt = currentFileName.contains(".") ?
                            currentFileName.substring(0, currentFileName.lastIndexOf('.')) : currentFileName;

                    if (selectedWellName != null && !selectedWellName.isEmpty()) {
                        // 前端指定了井名
                        filterWellName = selectedWellName;
                    } else {
                        // 尝试自动匹配：文件名包含井名 或 井名包含文件名
                        for (String wn : wellNames) {
                            if (fileNameNoExt.contains(wn) || wn.contains(fileNameNoExt)
                                    || fileNameNoExt.toLowerCase().contains(wn.toLowerCase())
                                    || wn.toLowerCase().contains(fileNameNoExt.toLowerCase())) {
                                filterWellName = wn;
                                break;
                            }
                        }
                    }

                    if (filterWellName == null) {
                        // 自动匹配失败，返回井名列表让前端选择
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("needSelectWell", true);
                        result.put("wellNames", new ArrayList<>(wellNames));
                        result.put("currentFileName", currentFileName);
                        return Result.success(result, "Excel包含多口井数据，请选择井名后重新导入");
                    }
                    log.info("井名自动匹配成功: {} -> {}", fileInfo.getFileName(), filterWellName);
                } else if (wellNames.size() == 1) {
                    filterWellName = wellNames.iterator().next();
                    log.info("Excel仅包含一口井: {}", filterWellName);
                }
            }

            List<WellLayer> layers = new ArrayList<>();
            // 从第二行开始读数据（第一行是表头）
            for (int i = 1; i < rows.size(); i++) {
                Map<Integer, String> row = rows.get(i);
                if (row == null || row.isEmpty()) continue;

                // 井名过滤：如果指定了井名，只导入匹配的井
                if (filterWellName != null && wellNameColIdx >= 0) {
                    String rowWellName = getStringValue(row, wellNameColIdx);
                    if (rowWellName == null || !rowWellName.equals(filterWellName)) continue;
                }

                String layerName = getStringValue(row, layerColIdx);
                String topStr = getStringValue(row, topColIdx);
                String bottomStr = getStringValue(row, bottomColIdx);
                String remark = remarkColIdx >= 0 ? getStringValue(row, remarkColIdx) : "";

                if (layerName == null || layerName.isEmpty()) continue;

                try {
                    java.math.BigDecimal topDepth = topStr != null && !topStr.isEmpty() ? new java.math.BigDecimal(topStr.trim()) : null;
                    java.math.BigDecimal bottomDepth = bottomStr != null && !bottomStr.isEmpty() ? new java.math.BigDecimal(bottomStr.trim()) : null;

                    WellLayer layer = new WellLayer();
                    layer.setFileId(id);
                    layer.setLayerName(layerName.trim());
                    layer.setTopDepth(topDepth);
                    layer.setBottomDepth(bottomDepth);
                    layer.setRemark(remark != null ? remark.trim() : "");
                    layer.setCreateTime(new Date());
                    layers.add(layer);
                } catch (NumberFormatException e) {
                    log.warn("跳过无效行 {}: topDepth={}, bottomDepth={}", i, topStr, bottomStr);
                }
            }

            if (layers.isEmpty()) {
                return Result.failed("未能从 Excel 中解析出有效的分层数据");
            }

            // 全量覆盖：先删后插
            wellLayerService.remove(new LambdaQueryWrapper<WellLayer>().eq(WellLayer::getFileId, id));
            wellLayerService.saveBatch(layers);

            operationLogService.recordLog("分层配置", "导入分层 [" + fileInfo.getFileName() + "] " + layers.size() + " 层" + (filterWellName != null ? " (井:" + filterWellName + ")" : ""), 1, null, userId);
            return Result.success("成功导入 " + layers.size() + " 条分层记录" + (filterWellName != null ? " (井:" + filterWellName + ")" : ""));
        } catch (Exception e) {
            log.error("文件导入分层失败", e);
            return Result.failed("文件解析失败，请确认文件格式");
        }
    }

    private String getStringValue(Map<Integer, String> row, int index) {
        String val = row.get(index);
        return val != null ? val.trim() : null;
    }

    /**
     * 检测文件编码（UTF-8 or GBK）
     */
    private java.nio.charset.Charset detectFileEncoding(MultipartFile file) {
        byte[] head = new byte[8192];
        int bytesRead;
        try (InputStream is = file.getInputStream()) {
            bytesRead = is.read(head);
        } catch (Exception e) {
            return java.nio.charset.Charset.forName("GBK");
        }
        if (bytesRead <= 0) return java.nio.charset.Charset.forName("GBK");

        int start = 0;
        if (bytesRead >= 3 && head[0] == (byte) 0xEF
                && head[1] == (byte) 0xBB && head[2] == (byte) 0xBF) {
            start = 3;
        }

        boolean hasHighBytes = false;
        int i = start;
        while (i < bytesRead) {
            int b = head[i] & 0xFF;
            int extraBytes;
            if (b <= 0x7F) {
                extraBytes = 0;
            } else if (b >= 0xC2 && b <= 0xDF) {
                extraBytes = 1;
                hasHighBytes = true;
            } else if (b >= 0xE0 && b <= 0xEF) {
                extraBytes = 2;
                hasHighBytes = true;
            } else if (b >= 0xF0 && b <= 0xF4) {
                extraBytes = 3;
                hasHighBytes = true;
            } else {
                return java.nio.charset.Charset.forName("GBK");
            }
            for (int j = 1; j <= extraBytes; j++) {
                if (i + j >= bytesRead) return java.nio.charset.Charset.forName("GBK");
                if ((head[i + j] & 0xC0) != 0x80) return java.nio.charset.Charset.forName("GBK");
            }
            i += 1 + extraBytes;
        }

        return hasHighBytes ? java.nio.charset.StandardCharsets.UTF_8 : java.nio.charset.Charset.forName("GBK");
    }

    /**
     * 解析 TXT 文件为行数据（自动检测编码 + 自动检测分隔符）
     */
    private List<Map<Integer, String>> parseTxtToRows(MultipartFile file) throws Exception {
        java.nio.charset.Charset charset = detectFileEncoding(file);
        String content = new String(file.getBytes(), charset);
        String[] lines = content.split("\r?\n");

        // 自动检测分隔符：看表头行中包含 \t 还是 ,
        String delimiter = ",";
        if (lines.length > 0) {
            String firstLine = lines[0];
            long tabCount = firstLine.chars().filter(c -> c == '\t').count();
            long commaCount = firstLine.chars().filter(c -> c == ',').count();
            if (tabCount > commaCount) {
                delimiter = "\t";
            }
        }

        List<Map<Integer, String>> rows = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] cols = line.split(delimiter, -1);
            Map<Integer, String> row = new LinkedHashMap<>();
            for (int i = 0; i < cols.length; i++) {
                row.put(i, cols[i].trim());
            }
            rows.add(row);
        }
        return rows;
    }
}
