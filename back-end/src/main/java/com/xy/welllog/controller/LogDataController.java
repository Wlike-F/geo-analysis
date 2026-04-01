package com.xy.welllog.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcel;
import com.xy.welllog.common.Result;
import com.xy.welllog.entity.LogDataRecord;
import com.xy.welllog.entity.LogFileInfo;
import com.xy.welllog.service.LogDataRecordService;
import com.xy.welllog.service.LogFileInfoService;
import com.xy.welllog.dto.LogDataQueryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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

    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> pageQuery(@RequestBody LogDataQueryDTO query) {
        Page<LogDataRecord> pageParam = new Page<>(query.getCurrent(), query.getSize());
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
    public void exportBatchZip(@RequestBody List<LogDataQueryDTO> queries, HttpServletResponse response) {
        try {
            response.setContentType("application/zip");
            response.setCharacterEncoding("utf-8");
            String zipName = URLEncoder.encode("批量测井数据导出", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + zipName + ".zip");

            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
                for (LogDataQueryDTO query : queries) {
                    if (query.getFileId() == null) continue;

                    LogFileInfo fileInfo = fileInfoService.getById(query.getFileId());
                    if (fileInfo == null) continue;

                    String fileName = fileInfo.getFileName() != null ? fileInfo.getFileName() : ("File_" + query.getFileId() + ".txt");
                    fileName = fileName.replaceAll("[\\\\/?*:\\[\\]]", "_") + ".xlsx";

                    query.setCurrent(1);
                    query.setSize(500000);

                    Page<Map<String, Object>> pageData = pageQuery(query).getData();
                    if (pageData == null || pageData.getRecords() == null || pageData.getRecords().isEmpty()) { continue; }

                    List<String> cols = JSONUtil.toList(fileInfo.getColumnsJson(), String.class);
                    List<List<String>> head = new ArrayList<>();
                    for(String col : cols) {
                        head.add(Collections.singletonList(col));
                    }

                    List<List<Object>> dataList = new ArrayList<>();
                    for(Map<String, Object> map : pageData.getRecords()) {
                        List<Object> row = new ArrayList<>();
                        for(String col : cols) {
                            Object val = map.get(col);
                            if (val == null) val = map.get(col.toLowerCase());
                            if (val == null) val = "";
                            row.add(val);
                        }
                        dataList.add(row);
                    }

                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(fileName);
                    zos.putNextEntry(zipEntry);

                    EasyExcel.write(zos)
                            .head(head)
                            .autoCloseStream(Boolean.FALSE)
                            .sheet("Filtered Data")
                            .doWrite(dataList);

                    zos.closeEntry();
                }
                zos.finish();
            }
        } catch (Exception e) {
            log.error("批量导出ZIP流异常", e);
            response.setStatus(500);
        }
    }

    @PostMapping("/{fileId}/export")

    public void exportExcel(@PathVariable Long fileId, @RequestBody(required = false) LogDataQueryDTO query, HttpServletResponse response) {
        if (query == null) query = new LogDataQueryDTO();
        query.setFileId(fileId);
        query.setCurrent(1);
        query.setSize(500000); 
        
        Page<Map<String, Object>> pageData = pageQuery(query).getData();
        
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("Filtered_Data_" + fileId, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
            
            LogFileInfo fileInfo = fileInfoService.getById(fileId);
            List<String> cols = JSONUtil.toList(fileInfo.getColumnsJson(), String.class);
            
            List<List<String>> head = new ArrayList<>();
            for(String col : cols) {
                head.add(Collections.singletonList(col));
            }

            List<List<Object>> dataList = new ArrayList<>();
            for(Map<String, Object> map : pageData.getRecords()) {
                 List<Object> row = new ArrayList<>();
                 for(String col : cols) {
                     Object val = map.get(col);
                     if (val == null) val = map.get(col.toLowerCase());
                     if (val == null) val = "";
                     row.add(val);
                 }
                 dataList.add(row);
            }

            EasyExcel.write(response.getOutputStream())
                    .head(head)
                    .sheet("Filtered Data")
                    .doWrite(dataList);
        } catch (Exception e) {
            log.error("导出Excel异常", e);
            response.setStatus(500);
        }
    }

    @GetMapping("/echarts/{fileId}")
    public Result<Map<String, List<Object>>> getEchartsData(@PathVariable Long fileId) {
        QueryWrapper<LogDataRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("file_id", fileId).orderByAsc("depth"); // 必须要按深度排序
        List<LogDataRecord> list = dataRecordService.list(wrapper);

        List<Object> depths = new ArrayList<>();
        List<Object> acs = new ArrayList<>();
        List<Object> dens = new ArrayList<>();
        List<Object> grs = new ArrayList<>();
        List<Object> sps = new ArrayList<>();
        List<Object> rts = new ArrayList<>();

        for (LogDataRecord record : list) {
            depths.add(record.getDepth());
            acs.add(record.getAc());
            dens.add(record.getDen());
            grs.add(record.getGr());
            sps.add(record.getSp());
            rts.add(record.getRt());
        }

        Map<String, List<Object>> result = new HashMap<>();
        result.put("DEPTH", depths);
        result.put("AC", acs);
        result.put("DEN", dens);
        result.put("GR", grs);
        result.put("SP", sps);
        result.put("RT", rts);

        return Result.success(result);
    }
}
