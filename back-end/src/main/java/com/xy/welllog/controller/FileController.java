package com.xy.welllog.controller;

import cn.hutool.core.io.FileUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xy.welllog.common.Result;
import com.xy.welllog.dto.WellLogDataDTO;
import com.xy.welllog.dto.WellLogFileDTO;
import com.xy.welllog.dto.PreviewResultDTO;
import com.xy.welllog.dto.ConfirmUploadDTO;
import com.xy.welllog.entity.LogFileInfo;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.service.*;
import com.xy.welllog.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.*;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
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

    private static final String UPLOAD_DIR = "E:\\Others\\upload"; // 主目录
    private static final String UPLOAD_DIR_TO = "C:\\project-upload"; // 备用目录
    // 改为 Linux 服务器上的绝对路径
    // private static final String UPLOAD_DIR = "/www/wwwroot/xy/upload";

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
            // 如果目录不存在，创建目录UPLOAD_DIR，创建失败的话
            // 启用临时目录UPLOAD_DIR_TO，UPLOAD_DIR_TO目录不存在则创建
            if (!FileUtil.exist(UPLOAD_DIR)) {
                boolean created = FileUtil.mkdir(UPLOAD_DIR) != null;
                if (!created || !FileUtil.exist(UPLOAD_DIR)) {
                    log.warn("主上传目录 [{}] 创建失败，启用备用目录 [{}]", UPLOAD_DIR, UPLOAD_DIR_TO);
                    if (!FileUtil.exist(UPLOAD_DIR_TO)) {
                        boolean backupCreated = FileUtil.mkdir(UPLOAD_DIR_TO) != null;
                        if (!backupCreated || !FileUtil.exist(UPLOAD_DIR_TO)) {
                            log.error("备用上传目录 [{}] 也创建失败", UPLOAD_DIR_TO);
                            return Result.failed("文件上传目录不可用");
                        }
                    }
                }
            }
            String originalFileName = file.getOriginalFilename();
            String tempName = UUID.randomUUID() + "_" + originalFileName;
            File destTempFile = new File(UPLOAD_DIR, tempName);
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
}
