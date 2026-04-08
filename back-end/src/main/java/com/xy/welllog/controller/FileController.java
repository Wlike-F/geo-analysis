package com.xy.welllog.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xy.welllog.common.Result;
import com.xy.welllog.dto.WellLogDataDTO;
import com.xy.welllog.dto.WellLogFileDTO;
import com.xy.welllog.entity.LogFileInfo;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.service.LogDataRecordService;
import com.xy.welllog.service.LogFileInfoService;
import com.xy.welllog.service.SysOperationLogService;
import com.xy.welllog.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xy.welllog.service.SysUserService;
import com.xy.welllog.entity.SysColumnMapping;
import com.xy.welllog.service.SysColumnMappingService;
import java.util.regex.Pattern;

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

    // private static final String UPLOAD_DIR = "E:\\Others\\upload";
    // 改为 Linux 服务器上的绝对路径
    private static final String UPLOAD_DIR = "/www/wwwroot/xy/upload";

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
     * 上传 TXT 测井文件至本地保存，并解析将其返回给前端
     */
    @PostMapping("/upload")
    public Result<WellLogFileDTO> uploadTxtFile(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (file.isEmpty()) {
            return Result.failed("文件内容为空");
        }
        try {
            if (!FileUtil.isDirectory(UPLOAD_DIR)) {
                FileUtil.mkdir(UPLOAD_DIR);
            }
            String fileName = file.getOriginalFilename();
            File destTempFile = null;
            if (fileName != null) {
                destTempFile = new File(UPLOAD_DIR, fileName);
            }
            if (destTempFile != null) {
                file.transferTo(destTempFile);
            }

            // 读取并解析文件
            WellLogFileDTO dto = parseTxtFile(destTempFile, fileName);
            int total = dto.getData().size();
            
            LogFileInfo fileInfo = new LogFileInfo();
            fileInfo.setUserId(getUserId(request));
            fileInfo.setFileName(fileName);
            fileInfo.setTotalRows(total);
            fileInfo.setStatus(1);
            fileInfo.setCreateTime(new Date());
            fileInfo.setColumnsJson(JSONUtil.toJsonStr(dto.getColumns()));
            fileInfoService.save(fileInfo);
            
            Long fileId = fileInfo.getId();
            dto.setFileId(fileId);
            dto.setTotalRows(total);
            
            List<Map<String, Object>> rawData = dto.getData();
            dto.setData(new ArrayList<>());
            try {
                dataRecordService.processAndSaveBatch(fileId, dto.getColumns(), rawData);
                log.info("同步落库写入宽表成功, fileName:{},fileId: {}, 记录数: {}",fileName, fileId, total);
            } catch (Exception e) {
                log.error("后台入库处理异常: ", e);
            }
            operationLogService.recordLog("文件加载", "单次上传测井档案 [" + fileName + "]", 1, (long) dto.getData().size(),getUserId(request));
            
            return Result.success(dto, "文件上传并解析成功，后台存储服务已接入");
        } catch (Exception e) {
            log.error("文件上传解析异常: ", e);
            return Result.failed("系统内部文件解析出错");
        }
    }

    /**
     * 读取服务器某个绝对路径下所有的 TXT 测井文件并返回
     */
    @GetMapping("/scan")
    public Result<List<WellLogFileDTO>> scanServerPath(@RequestParam("path") String path, HttpServletRequest request) {
        if (!FileUtil.isDirectory(path)) {
            return Result.failed("该路径不存在或不是一个目录");
        }

        List<File> txtFiles = FileUtil.loopFiles(path, pathname -> pathname.getName().toLowerCase().endsWith(".txt"));
        if (txtFiles == null || txtFiles.isEmpty()) {
            return Result.failed("指定目录下没有任何 .txt 格式的测井记录");
        }

        List<WellLogFileDTO> resultList = new ArrayList<>();
        long totalLines = 0;
        for (File txtFile : txtFiles) {
            try {
                WellLogFileDTO dto = parseTxtFile(txtFile, txtFile.getName());
                if (dto.getData() != null && !dto.getData().isEmpty()) {
                    int total = dto.getData().size();
                    
                    LogFileInfo fileInfo = new LogFileInfo();
                    fileInfo.setUserId(getUserId(request));
                    fileInfo.setFileName(txtFile.getName());
                    fileInfo.setTotalRows(total);
                    fileInfo.setStatus(1);
                    fileInfo.setCreateTime(new Date());
                    fileInfo.setColumnsJson(JSONUtil.toJsonStr(dto.getColumns()));
                    fileInfoService.save(fileInfo);
                    
                    Long fileId = fileInfo.getId();
                    dto.setFileId(fileId);
                    dto.setTotalRows(total);
                    dto.setTitle(txtFile.getName());
                    
                    List<Map<String, Object>> rawData = dto.getData();
                    dto.setData(new ArrayList<>());
                    
                    CompletableFuture.runAsync(() -> {
                        try {
                            dataRecordService.processAndSaveBatch(fileId, dto.getColumns(), rawData);
                        } catch (Exception e) {
                            log.error("后台异步入库处理异常: ", e);
                        }
                    });
                    
                    resultList.add(dto);
                    totalLines += total;
                }
            } catch (Exception e) {
                log.warn("跳过不合规的文件: {}", txtFile.getName());
            }
        }
        
        if (!resultList.isEmpty()) {
            operationLogService.recordLog("文件扫描", "批量扫描目录 [" + path + "] 加载了 " + resultList.size() + " 个文件", resultList.size(), totalLines, getUserId(request));
        }
        
        return Result.success(resultList, "批量扫描并解析完成，后台存储服务已接入");
    }

    /**
     * 前端传递过滤后的结果集，后端一键生成标准 Excel 并提供串流下载
     */
    @PostMapping("/export")
    public void exportToExcel(@RequestBody List<WellLogDataDTO> exportData, HttpServletResponse response) throws IOException {
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("地质测井数据归档报表", "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
            
            // 使用 EasyExcel 向 Response 对象写入数据流（利用反射机制，基于 @ExcelProperty 注解）
            EasyExcel.write(response.getOutputStream(), WellLogDataDTO.class)
                    .sheet("筛选归档数据")
                    .doWrite(exportData);
                    
            operationLogService.recordLog("报表导出", "单项数据归档导出", 1, (long) exportData.size(), getUserId(request));
        } catch (Exception e) {
            log.error("Excel 写入流离线异常", e);
        }
    }

    /**
     * 前端传递多文件过滤后的结果集，后端生成包含多个Sheet的 Excel 并提供串流下载
     */
    @PostMapping("/export-batch")
    public void exportBatchToExcel(@RequestBody List<WellLogFileDTO> exportDataList, HttpServletResponse response) throws IOException {
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("一键批量测井数据报表", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
            
            // 创建一个通用的 Writer
            try (ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream(), WellLogDataDTO.class).build()) {
                for (int i = 0; i < exportDataList.size(); i++) {
                    WellLogFileDTO fileDTO = exportDataList.get(i);
                    // 确保 sheet 名不包含非法字符且长度不超过限制
                    String sheetName = fileDTO.getTitle().replaceAll("[\\\\/?*:\\[\\]]", "_");
                    if (sheetName.length() > 31) {
                        sheetName = sheetName.substring(0, 31);
                    }
                    if (sheetName.isEmpty()) {
                        sheetName = "Sheet" + (i + 1);
                    }
                    WriteSheet writeSheet = EasyExcel.writerSheet(i, sheetName).build();
                    excelWriter.write(fileDTO.getData(), writeSheet);
                }
            }
            
            operationLogService.recordLog("报表导出", "一键批量多Sheet报表导出", exportDataList.size(), null, getUserId(request));
        } catch (Exception e) {
            log.error("批量导出Excel异常", e);
        }
    }

    /**
     * 前端传递多文件过滤后的结果集，后端将每个文件生成独立的 Excel 并打包为一个 ZIP 提供下载
     */
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
                    // 设置要放入ZIP里的excel文件名，例如 LT2.txt.xlsx -> LT2.xlsx
                    String excelFileName = fileDTO.getTitle();
                    if (excelFileName.toLowerCase().endsWith(".txt")) {
                        excelFileName = excelFileName.substring(0, excelFileName.length() - 4);
                    }
                    excelFileName += ".xlsx";

                    ZipEntry zipEntry = new ZipEntry(excelFileName);
                    zos.putNextEntry(zipEntry);

                    // 使用 EasyExcel 将数据写入到 ZOS，注意 autoCloseStream(false) 防止关闭底层的 zip流，不然只能下出1个表就崩了
                    List<List<String>> head = new ArrayList<>();
                    if (fileDTO.getColumns() != null) {
                        for (String col : fileDTO.getColumns()) {
                            head.add(Collections.singletonList(col));
                        }
                    }
                    List<List<Object>> dataList = new ArrayList<>();
                    if (fileDTO.getData() != null) {
                        for (Map<String, Object> map : fileDTO.getData()) {
                            List<Object> row = new ArrayList<>();
                            if (fileDTO.getColumns() != null) {
                                for (String col : fileDTO.getColumns()) {
                                    row.add(map.getOrDefault(col, ""));
                                }
                            }
                            dataList.add(row);
                        }
                    }

                    EasyExcel.write(zos)
                            .head(head)
                            .autoCloseStream(Boolean.FALSE)
                            .sheet("原始归档数据")
                            .doWrite(dataList);

                    zos.closeEntry();
                }
                zos.finish();
            }
            operationLogService.recordLog("报表导出", "批量ZIP数据压缩包导出", exportDataList.size(), null, getUserId(request));
        } catch (Exception e) {
            log.error("批量导出ZIP异常", e);
        }
    }

    /**
     * TXT提取引擎: 解析核心数据列，排除表头
     */
        private WellLogFileDTO parseTxtFile(File file, String title) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                lines = Files.readAllLines(file.toPath(), Charset.forName("GBK"));
            } catch (Exception ex) {
                lines = FileUtil.readLines(file, Charset.defaultCharset());
            }
        }

        // 加载字典规则并编译为正则表达式
        List<SysColumnMapping> rules = mappingService.list();
        Map<String, Pattern> compiledRules = new HashMap<>();
        for (SysColumnMapping rule : rules) {
            String ruleStr = rule.getStandardName().toUpperCase();
            String standardKey = rule.getStandardKey();
            String alias = rule.getAliasList();
            
            StringBuilder regexBuilder = new StringBuilder();
            regexBuilder.append("^(").append(ruleStr);
            if (standardKey != null && !standardKey.isEmpty()) {
                regexBuilder.append("|").append(standardKey);
            }
            if (alias != null && !alias.trim().isEmpty()) {
                regexBuilder.append("|").append(alias.replace(",", "|"));
            }
            regexBuilder.append(")$");
            
            compiledRules.put(ruleStr, Pattern.compile(regexBuilder.toString(), Pattern.CASE_INSENSITIVE));
        }

        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> data = new ArrayList<>();

        boolean headerFound = false;

        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            line = line.trim();
            String[] parts = line.split("\\s+");

            // Look for header row (if it contains letters)
            if (!headerFound) {
                boolean hasLetter = false;
                for (String p : parts) {
                    if (!isNumeric(p)) {
                        hasLetter = true;
                        break;
                    }
                }
                
                if (hasLetter) {
                    for (String part : parts) {
                        String mappedCol = part;
                        for (Map.Entry<String, Pattern> entry : compiledRules.entrySet()) {
                            if (entry.getValue().matcher(part.trim()).matches()) {
                                mappedCol = entry.getKey();
                                break;
                            }
                        }
                        // 处理同一个文件内多个列被映射成同一个标准列名(如都映射为 DEPTH) 的重复问题
                        if (columns.contains(mappedCol)) {
                            int suffix = 1;
                            String uniqueCol = mappedCol;
                            while (columns.contains(uniqueCol)) {
                                uniqueCol = mappedCol + "_" + suffix;
                                suffix++;
                            }
                            mappedCol = uniqueCol;
                        }
                        columns.add(mappedCol);
                    }
                    headerFound = true;
                    continue;
                } else {
                    // No clear header, generate default
                    for (int i = 0; i < parts.length; i++) {
                        columns.add("Col" + (i + 1));
                    }
                    headerFound = true;
                    // Fallthrough to data processing
                }
            }

            if (parts.length >= columns.size() || parts.length > 2) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < parts.length; i++) {
                    String colName = i < columns.size() ? columns.get(i) : "ExtraCol" + i;
                    try {
                        // Store as double to maintain numeric integrity, fallback to string
                        row.put(colName, String.format("%.4f", Double.parseDouble(parts[i])));
                    } catch (NumberFormatException e) {
                        row.put(colName, parts[i]);
                    }
                }
                data.add(row);
            }
        }
        
        WellLogFileDTO dto = new WellLogFileDTO();
        dto.setTitle(title);
        dto.setColumns(columns);
        dto.setData(data);
        return dto;
    }

    @GetMapping("/list")
    public Result<List<LogFileInfo>> getFileList() {
        LambdaQueryWrapper<LogFileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogFileInfo::getStatus, 1).orderByDesc(LogFileInfo::getCreateTime);
        return Result.success(fileInfoService.list(wrapper));
    }

    @GetMapping("/page")
    public Result<Page<LogFileInfo>> getFilePage(@RequestParam(defaultValue = "1") Integer current,
                                                 @RequestParam(defaultValue = "10") Integer size,
                                                 @RequestParam(required = false) String fileName,
                                                 HttpServletRequest request) {
        Page<LogFileInfo> page = new Page<>(current, size);
        LambdaQueryWrapper<LogFileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogFileInfo::getStatus, 1)
                .eq(LogFileInfo::getUserId, getUserId(request));
        if (StringUtils.hasText(fileName)) {
            wrapper.like(LogFileInfo::getFileName, fileName);
        }
        wrapper.orderByDesc(LogFileInfo::getCreateTime);
        return Result.success(fileInfoService.page(page, wrapper));
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteFile(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        LogFileInfo fileInfo = fileInfoService.getById(id);
        if (fileInfo == null || !fileInfo.getUserId().equals(userId)) {
            return Result.failed("文件不存在或无权删除");
        }
        
        fileInfo.setStatus(0);
        fileInfoService.updateById(fileInfo);
        
        // 记录操作日志
        operationLogService.recordLog("文件管理", "删除文件 [" + fileInfo.getFileName() + "]", 1, null, userId);

        CompletableFuture.runAsync(() -> {
            try {
                // 删除关联的宽表数据
                // 暂时注释掉，如果需要真实删除数据可放开
                // dataRecordService.deleteByFileId(id);
            } catch (Exception e) {
                log.error("删除关联数据异常: ", e);
            }
        });

        return Result.success("删除成功");
    }

    @DeleteMapping("/clear")
    public Result<String> clearFiles(HttpServletRequest request) {
        Long userId = getUserId(request);
        
        LambdaQueryWrapper<LogFileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogFileInfo::getUserId, userId).eq(LogFileInfo::getStatus, 1);
        List<LogFileInfo> list = fileInfoService.list(wrapper);
        
        if (!list.isEmpty()) {
            list.forEach(item -> item.setStatus(0));
            fileInfoService.updateBatchById(list);
            
            operationLogService.recordLog("文件管理", "清空所有文件，共 " + list.size() + " 个", list.size(), null, userId);

            CompletableFuture.runAsync(() -> {
                try {
                    // for (LogFileInfo fileInfo : list) {
                    //     dataRecordService.deleteByFileId(fileInfo.getId());
                    // }
                } catch (Exception e) {
                    log.error("清空关联数据异常: ", e);
                }
            });
        }
        
        return Result.success("清空成功");
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
