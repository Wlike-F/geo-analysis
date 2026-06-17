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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Pattern;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.exception.ExcelAnalysisStopException;
import com.alibaba.excel.read.listener.ReadListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogFileParseService {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$");

    private final SysColumnMappingService mappingService;
    private final LogDataRecordService dataRecordService;
    private final LogFileInfoService fileInfoService;
    private final JdbcTemplate jdbcTemplate;


    private List<String> extractLinesFromFile(File file, String title, int limitLines) {
        List<String> list = new ArrayList<>();
        String lowerTitle = title.toLowerCase();
        try {
            if (lowerTitle.endsWith(".csv")) {
                // CSV: 自动检测编码 + BufferedReader 流式读取（避免全量加载）
                Charset csvCharset = detectFileEncoding(file);
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), csvCharset))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim().replace("\uFEFF", "");
                        if (line.isEmpty()) continue;
                        // 将逗号分隔转为空格分隔，保持和 TXT 相同的下游处理逻辑
                        list.add(line.replaceAll(",", " "));
                        if (limitLines > 0 && list.size() >= limitLines) break;
                    }
                }
            } else if (lowerTitle.endsWith(".xls") || lowerTitle.endsWith(".xlsx")) {
                // Excel: SAX 流式读取，逐行回调，支持提前停止
                EasyExcel.read(file, (ReadListener<Map<Integer, String>>) new ReadListener<Map<Integer, String>>() {
                    @Override
                    public void invoke(Map<Integer, String> row, AnalysisContext context) {
                        if (limitLines > 0 && list.size() >= limitLines) {
                            throw new ExcelAnalysisStopException();
                        }
                        String line = convertExcelRowToString(row);
                        if (!line.isEmpty()) {
                            list.add(line);
                        }
                    }
                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) { /* 读取完毕 */ }
                }).sheet().headRowNumber(0).doRead();
            } else {
                // TXT: GBK 编码流式读取
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), "GBK"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            list.add(line.trim().replace("\uFEFF", ""));
                            if (limitLines > 0 && list.size() >= limitLines) break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed reading lines for " + file.getName(), e);
        }
        return list;
    }

    /**
     * 自动检测文件编码（UTF-8 vs GBK）。
     * 通过检查前 8KB 字节是否为合法 UTF-8 序列来判断。
     */
    private Charset detectFileEncoding(File file) {
        byte[] head = new byte[8192];
        int bytesRead;
        try (InputStream is = new FileInputStream(file)) {
            bytesRead = is.read(head);
        } catch (Exception e) {
            return Charset.forName("GBK");
        }
        if (bytesRead <= 0) return Charset.forName("GBK");

        // 跳过 UTF-8 BOM
        int start = 0;
        if (bytesRead >= 3 && head[0] == (byte) 0xEF
                && head[1] == (byte) 0xBB && head[2] == (byte) 0xBF) {
            start = 3;
        }

        // 校验是否为合法 UTF-8 多字节序列
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
                return Charset.forName("GBK"); // 非法 UTF-8 起始字节
            }
            for (int j = 1; j <= extraBytes; j++) {
                if (i + j >= bytesRead) return Charset.forName("GBK");
                if ((head[i + j] & 0xC0) != 0x80) return Charset.forName("GBK");
            }
            i += 1 + extraBytes;
        }

        // 全是 ASCII 或合法 UTF-8 多字节 → 用 UTF-8
        return hasHighBytes ? java.nio.charset.StandardCharsets.UTF_8 : Charset.forName("GBK");
    }


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

        List<String> probeLines = extractLinesFromFile(file, title, 100);

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
        List<String> frontLines = extractLinesFromFile(file, title, 200);
        try {
            boolean dataStarted = false;
            for (String line : frontLines) {
                if (previewData.size() >= 50) break;
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
     * 将 EasyExcel 读取的一行 Map<Integer,String> 转为空格分隔的字符串
     */
    private String convertExcelRowToString(Map<Integer, String> row) {
        if (row == null) return "";
        int maxCol = row.keySet().stream().max(Integer::compareTo).orElse(-1);
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j <= maxCol; j++) {
            String val = row.get(j);
            val = val == null ? "" : val.trim().replace("\uFEFF", "").replaceAll("\\s+", "_");
            sb.append(val).append(" ");
        }
        return sb.toString().trim();
    }

    /**
     * 2. 异步解析逻辑：根据文件类型分流 + 字典规则只编译一次 + 使用指定的 columns 映射落库
     */
    @Async("logFileExecutor")
    public void asyncParseAndSaveTxtStream(File file, String title, Long fileId, List<String> columns) {
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) return;

        // 字典规则只编译一次，后续所有批次复用
        Map<String, Pattern> compiledRules = compileMappingRules();

        String lowerTitle = title.toLowerCase();
        boolean isExcel = lowerTitle.endsWith(".xls") || lowerTitle.endsWith(".xlsx");
        boolean isCsv = lowerTitle.endsWith(".csv");

        // 共享状态（在监听器/循环中复用）
        final int[] totalRows = {0};
        final long[] lineNum = {0};
        final boolean[] dataStarted = {false};
        final List<Map<String, Object>> batch = new ArrayList<>(2000);

        try {
            if (isExcel) {
                // Excel: SAX 流式监听器，逐行回调，每 2000 行批量入库
                EasyExcel.read(file, (ReadListener<Map<Integer, String>>) new ReadListener<Map<Integer, String>>() {
                    @Override
                    public void invoke(Map<Integer, String> row, AnalysisContext context) {
                        String dataLine = convertExcelRowToString(row);
                        processParsedLine(dataLine, fileId, columns, compiledRules,
                                totalRows, lineNum, dataStarted, batch);
                    }
                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        if (!batch.isEmpty()) {
                            dataRecordService.processAndSaveBatch(fileId, columns, batch, compiledRules);
                            batch.clear();
                        }
                    }
                }).sheet().headRowNumber(0).doRead();

            } else {
                // CSV/TXT: BufferedReader 流式逐行读取
                Charset charset = isCsv ? detectFileEncoding(file) : Charset.forName("GBK");
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), charset))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim().replace("\uFEFF", "");
                        if (line.isEmpty()) continue;
                        if (isCsv) line = line.replaceAll(",", " ");
                        processParsedLine(line, fileId, columns, compiledRules,
                                totalRows, lineNum, dataStarted, batch);
                    }
                }
                if (!batch.isEmpty()) {
                    dataRecordService.processAndSaveBatch(fileId, columns, batch, compiledRules);
                    batch.clear();
                }
            }

            fileInfo.setTotalRows(totalRows[0]);
            fileInfo.setColumnsJson(JSONUtil.toJsonStr(columns));
            fileInfo.setStatus(1); // 成功
            fileInfoService.updateById(fileInfo);
            log.info("异步解析完成: {}, 文件ID: {}, 共 {} 行", title, fileId, totalRows[0]);

        } catch (Exception e) {
            log.error("异步解析异常: fileId={}", fileId, e);
            fileInfo.setStatus(-1); // 失败
            fileInfoService.updateById(fileInfo);
        }
    }

    /**
     * 处理单行解析后的数据（表头检测 → 脏数据过滤 → 批量入库）。
     * 由 Excel 监听器和 TXT/CSV 流式读取共同复用。
     */
    private void processParsedLine(String dataLine, Long fileId, List<String> columns,
                                   Map<String, Pattern> compiledRules,
                                   int[] totalRows, long[] lineNum, boolean[] dataStarted,
                                   List<Map<String, Object>> batch) {
        lineNum[0]++;
        if (dataLine.trim().isEmpty()) return;
        String[] parts = dataLine.trim().split("\\s+");

        if (!dataStarted[0]) {
            int numCount = 0;
            for (String p : parts) {
                if (NUMERIC_PATTERN.matcher(p).matches()) numCount++;
            }
            if (parts.length >= 2 && numCount >= parts.length / 2.0) {
                dataStarted[0] = true;
            } else {
                return;
            }
        }

        if (parts.length > columns.size() + 3 || parts.length < columns.size() / 2.0) {
            jdbcTemplate.update("INSERT INTO log_dirty_data (file_id, line_num, raw_content) VALUES (?, ?, ?)",
                    fileId, lineNum[0], dataLine);
            return;
        }

        Map<String, Object> rowMap = new LinkedHashMap<>();
        for (int i = 0; i < parts.length; i++) {
            String colName = i < columns.size() ? columns.get(i) : "ExtraCol" + (i + 1);
            if (NUMERIC_PATTERN.matcher(parts[i]).matches()) {
                rowMap.put(colName, String.format("%.4f", Double.parseDouble(parts[i])));
            } else {
                rowMap.put(colName, parts[i]);
            }
        }
        batch.add(rowMap);
        totalRows[0]++;

        if (batch.size() >= 2000) {
            dataRecordService.processAndSaveBatch(fileId, columns, batch, compiledRules);
            batch.clear();
        }
    }

    /**
     * 编译字典映射规则为正则表达式（只查库一次）
     */
    private Map<String, Pattern> compileMappingRules() {
        List<SysColumnMapping> rules = mappingService.list();
        Map<String, Pattern> compiledRules = new HashMap<>();
        for (SysColumnMapping rule : rules) {
            String ruleStr = rule.getStandardKey().toLowerCase();
            String alias = rule.getAliasList();
            String standardName = rule.getStandardName();

            StringBuilder regexBuilder = new StringBuilder();
            regexBuilder.append("^(").append(rule.getStandardKey());
            if (standardName != null && !standardName.isEmpty()) {
                regexBuilder.append("|").append(standardName);
            }
            if (alias != null && !alias.trim().isEmpty()) {
                regexBuilder.append("|").append(alias.replace(",", "|"));
            }
            regexBuilder.append(")$");

            compiledRules.put(ruleStr, Pattern.compile(regexBuilder.toString(), Pattern.CASE_INSENSITIVE));
        }
        return compiledRules;
    }
}
