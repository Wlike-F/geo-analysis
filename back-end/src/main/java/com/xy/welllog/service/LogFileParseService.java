package com.xy.welllog.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xy.welllog.dto.PreviewResultDTO;
import com.xy.welllog.entity.LogFileInfo;
import com.xy.welllog.entity.SysColumnMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
                // TXT: 自动检测编码 + 流式读取
                Charset txtCharset = detectFileEncoding(file);
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), txtCharset))) {
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
            String standardName = rule.getStandardName();
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
    public void asyncParseAndSaveTxtStream(File file, String title, Long fileId,
                                           List<String> columns, Map<String, String> textColumns) {
        log.info("[解析] 开始异步解析: {}, fileId={}, 列数={}", title, fileId, columns != null ? columns.size() : 0);
        LogFileInfo fileInfo = fileInfoService.getById(fileId);
        if (fileInfo == null) {
            log.warn("[解析] 文件不存在, fileId={}", fileId);
            return;
        }

        // 字典规则只编译一次，后续所有批次复用
        Map<String, Pattern> compiledRules = compileMappingRules();
        log.debug("[解析] 编译字典规则: {} 条", compiledRules.size());

        String lowerTitle = title.toLowerCase();
        boolean isExcel = lowerTitle.endsWith(".xls") || lowerTitle.endsWith(".xlsx");
        boolean isCsv = lowerTitle.endsWith(".csv");
        log.info("[解析] 文件类型: {}, 编码检测: {}", isExcel ? "Excel" : (isCsv ? "CSV" : "TXT"),
                isExcel ? "无需检测" : "自动检测");

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
                                totalRows, lineNum, dataStarted, batch, textColumns);
                    }
                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        if (!batch.isEmpty()) {
                            dataRecordService.processAndSaveBatch(fileId, columns, batch, compiledRules, textColumns);
                            batch.clear();
                        }
                    }
                }).sheet().headRowNumber(0).doRead();

            } else {
                // CSV/TXT: 多线程分块并行解析
                Charset charset = detectFileEncoding(file);
                log.info("[解析] 编码检测结果: {}, 文件: {}", charset.name(), title);
                long fileSize = file.length();
                int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), 4);
                // 文件小于 10MB 不启用多线程
                if (fileSize < 10 * 1024 * 1024 || numThreads < 2) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(new FileInputStream(file), charset))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            line = line.trim().replace("\uFEFF", "");
                            if (line.isEmpty()) continue;
                            if (isCsv) line = line.replace(",", " ");
                            processParsedLine(line, fileId, columns, compiledRules,
                                    totalRows, lineNum, dataStarted, batch, textColumns);
                        }
                    }
                    if (!batch.isEmpty()) {
                        dataRecordService.processAndSaveBatch(fileId, columns, batch, compiledRules, textColumns);
                        batch.clear();
                    }
                } else {
                    log.info("[解析] 启用 {} 线程并行解析, 文件大小 {} MB", numThreads, fileSize / 1024 / 1024);
                    long chunkSize = fileSize / numThreads;
                    AtomicInteger atomicTotalRows = new AtomicInteger(0);
                    ExecutorService pool = Executors.newFixedThreadPool(numThreads);
                    List<Future<?>> futures = new ArrayList<>();

                    for (int i = 0; i < numThreads; i++) {
                        final long chunkStart = i * chunkSize;
                        final long chunkEnd = (i == numThreads - 1) ? fileSize : (i + 1) * chunkSize;
                        final int chunkIdx = i;
                        futures.add(pool.submit(() -> {
                            parseChunk(file, charset, isCsv, chunkStart, chunkEnd, chunkIdx,
                                    fileId, columns, compiledRules, textColumns, atomicTotalRows);
                        }));
                    }

                    pool.shutdown();
                    for (Future<?> f : futures) {
                        try { f.get(30, TimeUnit.MINUTES); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.error("[解析] 线程被中断", e);
                        } catch (Exception e) {
                        }
                    }
                    totalRows[0] = atomicTotalRows.get();
                }
            }

            fileInfo.setTotalRows(totalRows[0]);
            fileInfo.setColumnsJson(JSONUtil.toJsonStr(columns));
            fileInfo.setStatus(1); // 成功
            fileInfoService.updateById(fileInfo);
            log.info("[解析] 解析完成: {}, fileId={}, 共 {} 行有效数据", title, fileId, totalRows[0]);

            // 预计算核心列统计（异步）。注意：这里只回写 column_stats_json 单列，
            // 绝不能用 getById+updateById 整行覆盖，否则会与上面的 status=1 形成竞态，
            // 把 status/totalRows 冲回旧值(0)，导致界面一直“处理中”。
            final Long statsFileId = fileId;
            CompletableFuture.runAsync(() -> {
                String stats = computeColumnStats(statsFileId);
                if (stats != null) {
                    fileInfoService.update(new LambdaUpdateWrapper<LogFileInfo>()
                            .eq(LogFileInfo::getId, statsFileId)
                            .set(LogFileInfo::getColumnStatsJson, stats));
                }
            });

        } catch (Exception e) {
            log.error("[解析] 解析异常: fileId={}, 已处理 {} 行", fileId, totalRows[0], e);
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
                                   List<Map<String, Object>> batch, Map<String, String> textColumns) {
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
            dataRecordService.processAndSaveBatch(fileId, columns, batch, compiledRules, textColumns);
            batch.clear();
        }
    }

    /**
     * 单线程解析文件的一个字节区间 [chunkStart, chunkEnd)。
     */
    private void parseChunk(File file, Charset charset, boolean isCsv,
                            long chunkStart, long chunkEnd, int chunkIdx,
                            Long fileId, List<String> columns,
                            Map<String, Pattern> compiledRules,
                            Map<String, String> textColumns,
                            AtomicInteger totalRows) {
        final int[] localRows = {0};
        final long[] lineNum = {0};
        final boolean[] dataStarted = {chunkIdx > 0};
        final List<Map<String, Object>> batch = new ArrayList<>(2000);

        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            long skipped = 0;
            while (skipped < chunkStart) {
                long s = in.skip(chunkStart - skipped);
                if (s <= 0) break;
                skipped += s;
            }
            // 非首块：跳过行残片
            if (chunkIdx > 0) {
                byte[] buf = new byte[1];
                while (in.read(buf) != -1 && buf[0] != '\n');
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(in, charset));
            String line;
            long bytesRead = chunkStart;
            int chunkCount = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim().replace("\uFEFF", "");
                if (line.isEmpty()) continue;
                bytesRead += line.getBytes(charset).length + 1;
                if (isCsv) line = line.replaceAll(",", " ");
                processParsedLine(line, fileId, columns, compiledRules,
                        localRows, lineNum, dataStarted, batch, textColumns);
                chunkCount++;
                if (bytesRead > chunkEnd) break;
            }
            if (!batch.isEmpty()) {
                dataRecordService.processAndSaveBatch(fileId, columns, batch, compiledRules, textColumns);
            }
            log.debug("[解析] 分块 {}: 处理 {} 行, 入库 {} 行", chunkIdx, chunkCount, localRows[0]);
        } catch (Exception e) {
            log.error("[解析] 分块 {} 异常: fileId={}", chunkIdx, fileId, e);
        }
        totalRows.addAndGet(localRows[0]);
    }

    /**
     * 编译字典映射规则为正则表达式（只查库一次）
     */
    /**
     * 解析完成后预计算核心列统计（MIN/MAX/有效数/无效数），存 JSON 供解析报告秒开
     */
    public String computeColumnStats(Long fileId) {
        try {
            String sentinel = "(col IS NOT NULL AND col != -9999 AND col != -999.25 AND col != -999)";
            String[] cols = {"depth", "ac", "den", "gr", "sp", "rt"};
            StringBuilder sb = new StringBuilder("SELECT ");
            for (int i = 0; i < cols.length; i++) {
                String c = cols[i], cond = sentinel.replace("col", c);
                if (i > 0) sb.append(", ");
                sb.append("MIN(CASE WHEN ").append(cond).append(" THEN ").append(c).append(" END) AS ").append(c).append("_min, ");
                sb.append("MAX(CASE WHEN ").append(cond).append(" THEN ").append(c).append(" END) AS ").append(c).append("_max, ");
                sb.append("SUM(CASE WHEN ").append(cond).append(" THEN 1 ELSE 0 END) AS ").append(c).append("_valid, ");
                sb.append("SUM(CASE WHEN ").append(cond).append(" THEN 0 ELSE 1 END) AS ").append(c).append("_invalid");
            }
            sb.append(" FROM log_data_records WHERE file_id = ?");
            Map<String, Object> row = jdbcTemplate.queryForMap(sb.toString(), fileId);
            Map<String, Object> stats = new LinkedHashMap<>();
            for (String col : cols) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("min", row.get(col + "_min"));
                s.put("max", row.get(col + "_max"));
                s.put("valid", row.get(col + "_valid"));
                s.put("invalid", row.get(col + "_invalid"));
                stats.put(col, s);
            }
            return JSONUtil.toJsonStr(stats);
        } catch (Exception e) {
            log.warn("[解析] 预计算列统计失败 fileId={}: {}", fileId, e.getMessage());
            return null;
        }
    }

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

            compiledRules.put(rule.getStandardName(), Pattern.compile(regexBuilder.toString(), Pattern.CASE_INSENSITIVE));
        }
        return compiledRules;
    }
}
