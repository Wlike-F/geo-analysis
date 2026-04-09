package com.xy.welllog.service;

import cn.hutool.json.JSONUtil;
import com.xy.welllog.dto.PreviewResultDTO;
import com.xy.welllog.entity.LogFileInfo;
import com.xy.welllog.entity.SysColumnMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogFileParseService {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$");

    private final SysColumnMappingService mappingService;
    private final LogDataRecordService dataRecordService;
    private final LogFileInfoService fileInfoService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 1. 预览逻辑：读取前100行找表头，提取建议映射和原始表头，并提取前50行预览数据。
     */
    public PreviewResultDTO previewTxtStreamSync(File file, String title) {
        PreviewResultDTO result = new PreviewResultDTO();
        result.setOriginalFileName(title);
        
        List<SysColumnMapping> rules = mappingService.list();
        Map<String, Pattern> compiledRules = new HashMap<>();
        for (SysColumnMapping rule : rules) {
            String standardName = rule.getStandardName().toUpperCase();
            String standardKey = rule.getStandardKey();
            String alias = rule.getAliasList();
            StringBuilder regexBuilder = new StringBuilder();
            regexBuilder.append("^(").append(Pattern.quote(standardName));
            if (standardKey != null && !standardKey.isEmpty()) {
                regexBuilder.append("|").append(Pattern.quote(standardKey));
            }
            if (alias != null && !alias.trim().isEmpty()) {
                for (String a : alias.split(",")) {
                    if (!a.trim().isEmpty()) {
                        regexBuilder.append("|").append(Pattern.quote(a.trim()));
                    }
                }
            }
            regexBuilder.append(")$");
            compiledRules.put(standardName, Pattern.compile(regexBuilder.toString(), Pattern.CASE_INSENSITIVE));
        }

        List<String> probeLines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "GBK"))) {
            String line;
            int lineCounter = 0;
            while ((line = br.readLine()) != null && lineCounter < 100) {
                if (!line.trim().isEmpty()) {
                    probeLines.add(line.trim());
                    lineCounter++;
                }
            }
        } catch (Exception e) {
            log.error("预览读取异常", e);
        }

        int bestScore = -1, headerIndex = -1;
        List<String> bestColumns = new ArrayList<>();
        List<String> originalHeaders = new ArrayList<>();

        for (int k = 0; k < probeLines.size(); k++) {
            String[] pParts = probeLines.get(k).split("\\s+");
            int matchCount = 0;
            int numCount = 0;
            List<String> tempCols = new ArrayList<>();
            for (String p : pParts) {
                if (NUMERIC_PATTERN.matcher(p).matches()) numCount++;
                String mappedCol = p;
                for (Map.Entry<String, Pattern> entry : compiledRules.entrySet()) {
                    if (entry.getValue().matcher(p.trim()).matches()) {
                        mappedCol = entry.getKey();
                        matchCount++;
                        break;
                    }
                }
                if (tempCols.contains(mappedCol)) {
                    int suffix = 1;
                    String uniqueCol = mappedCol;
                    while (tempCols.contains(uniqueCol)) uniqueCol = mappedCol + "_" + suffix++;
                    mappedCol = uniqueCol;
                }
                tempCols.add(mappedCol);
            }

            // 如果数值过半且长度>=2，判定数据已开始，停止探测表头
            if (pParts.length >= 2 && numCount >= pParts.length / 2.0) break;

            int score = (int) (((double) matchCount / pParts.length) * 100);
            if (score > bestScore) {
                bestScore = score;
                headerIndex = k;
                bestColumns = tempCols;
                originalHeaders = Arrays.asList(pParts);
            }
        }

        if (headerIndex == -1 || bestColumns.isEmpty()) {
            if (!probeLines.isEmpty()) {
                String[] firstLineParts = probeLines.get(0).split("\\s+");
                for (int i = 0; i < firstLineParts.length; i++) {
                    bestColumns.add("Col" + (i + 1));
                    originalHeaders.add("Col" + (i + 1));
                }
            }
        }

        result.setSuggestedMapping(bestColumns);
        result.setOriginalHeaders(originalHeaders);

        // 提取前50行数据
        List<Map<String, Object>> previewData = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "GBK"))) {
            String line;
            boolean dataStarted = false;
            while ((line = br.readLine()) != null && previewData.size() < 50) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.trim().split("\\s+");
                if (!dataStarted) {
                    int numCount = 0;
                    for (String p : parts) {
                        if (NUMERIC_PATTERN.matcher(p).matches()) numCount++;
                    }
                    if (parts.length >= 2 && numCount >= parts.length / 2.0) {
                        dataStarted = true;
                    } else {
                        continue;
                    }
                }

                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < parts.length; i++) {
                    String colName = i < bestColumns.size() ? bestColumns.get(i) : "ExtraCol" + (i + 1);
                    if (NUMERIC_PATTERN.matcher(parts[i]).matches()) {
                        row.put(colName, String.format("%.4f", Double.parseDouble(parts[i])));
                    } else {
                        row.put(colName, parts[i]);
                    }
                }
                previewData.add(row);
            }
        } catch (Exception e) {
            log.error("提取预览数据异常", e);
        }
        result.setPreviewData(previewData);
        return result;
    }

    /**
     * 2. 异步解析逻辑：使用指定的 columns 映射落库，脏数据存入 log_dirty_data
     */
    @Async("logFileExecutor")
    public void asyncParseAndSaveTxtStream(File file, String title, Long fileId, List<String> columns) {
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) return;

        int totalRows = 0;
        long lineNum = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "GBK"))) {
            String dataLine;
            boolean dataStarted = false;
            List<Map<String, Object>> batch = new ArrayList<>(2000);

            while ((dataLine = br.readLine()) != null) {
                lineNum++;
                if (dataLine.trim().isEmpty()) continue;
                String[] parts = dataLine.trim().split("\\s+");

                if (!dataStarted) {
                    int numCount = 0;
                    for (String p : parts) {
                        if (NUMERIC_PATTERN.matcher(p).matches()) numCount++;
                    }
                    if (parts.length >= 2 && numCount >= parts.length / 2.0) {
                        dataStarted = true;
                    } else {
                        continue;
                    }
                }

                // 脏数据判断逻辑：长度远大于设定的列数或小于一半
                if (parts.length > columns.size() + 3 || parts.length < columns.size() / 2.0) {
                    jdbcTemplate.update("INSERT INTO log_dirty_data (file_id, line_num, raw_content) VALUES (?, ?, ?)", 
                                        fileId, lineNum, dataLine);
                    continue;
                }

                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < parts.length; i++) {
                    String colName = i < columns.size() ? columns.get(i) : "ExtraCol" + (i + 1);
                    if (NUMERIC_PATTERN.matcher(parts[i]).matches()) {
                        row.put(colName, String.format("%.4f", Double.parseDouble(parts[i])));
                    } else {
                        row.put(colName, parts[i]);
                    }
                }
                batch.add(row);
                totalRows++;

                if (batch.size() >= 2000) {
                    dataRecordService.processAndSaveBatch(fileId, columns, batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                dataRecordService.processAndSaveBatch(fileId, columns, batch);
            }

            fileInfo.setTotalRows(totalRows);
            fileInfo.setColumnsJson(JSONUtil.toJsonStr(columns));
            fileInfo.setStatus(1); // 成功
            fileInfoService.updateById(fileInfo);
            log.info("异步解析完成: {}, 文件ID: {}, 共 {} 行", title, fileId, totalRows);

        } catch (Exception e) {
            log.error("异步解析异常: fileId={}", fileId, e);
            fileInfo.setStatus(-1); // 失败
            fileInfoService.updateById(fileInfo);
        }
    }
}
