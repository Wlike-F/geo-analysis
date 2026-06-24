package com.xy.welllog.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xy.welllog.entity.LogDataRecord;
import com.xy.welllog.mapper.LogDataRecordMapper;
import com.xy.welllog.service.LogDataRecordService;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class LogDataRecordServiceImpl extends ServiceImpl<LogDataRecordMapper, LogDataRecord> implements LogDataRecordService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
        "INSERT INTO log_data_records (file_id, depth, ac, den, gr, sp, rt, extra_json, " +
        "text_col_1, text_col_2, text_col_3, text_col_4, text_col_5, " +
        "text_col_6, text_col_7, text_col_8, text_col_9, text_col_10) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * 真批量 INSERT：一次 JDBC batch 提交多行，单次网络往返。
     * 比 MyBatis-Plus saveBatch（逐条 INSERT）快 50~100 倍。
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchInsert(List<LogDataRecord> records) {
        jdbcTemplate.batchUpdate(INSERT_SQL, records, 2000, (PreparedStatement ps, LogDataRecord r) -> {
            ps.setLong(1, r.getFileId());
            setBigDecimal(ps, 2, r.getDepth());
            setBigDecimal(ps, 3, r.getAc());
            setBigDecimal(ps, 4, r.getDen());
            setBigDecimal(ps, 5, r.getGr());
            setBigDecimal(ps, 6, r.getSp());
            setBigDecimal(ps, 7, r.getRt());
            ps.setString(8, r.getExtraJson() != null ? JSONUtil.toJsonStr(r.getExtraJson()) : null);
            ps.setString(9, r.getTextCol1());
            ps.setString(10, r.getTextCol2());
            ps.setString(11, r.getTextCol3());
            ps.setString(12, r.getTextCol4());
            ps.setString(13, r.getTextCol5());
            ps.setString(14, r.getTextCol6());
            ps.setString(15, r.getTextCol7());
            ps.setString(16, r.getTextCol8());
            ps.setString(17, r.getTextCol9());
            ps.setString(18, r.getTextCol10());
        });
    }

    private void setBigDecimal(PreparedStatement ps, int index, BigDecimal val) throws SQLException {
        if (val != null) {
            ps.setBigDecimal(index, val);
        } else {
            ps.setNull(index, java.sql.Types.DECIMAL);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processAndSaveBatch(Long fileId, List<String> columns,
                                    List<Map<String, Object>> parsedData,
                                    Map<String, Pattern> compiledRules,
                                    @Nullable Map<String, String> textColumns) {
        // 预计算列名→标准键映射，避免逐行正则
        Map<String, String> columnKeyMap = new HashMap<>();
        for (String col : columns) {
            for (Map.Entry<String, Pattern> ruleEntry : compiledRules.entrySet()) {
                if (ruleEntry.getValue().matcher(col).matches()) {
                    columnKeyMap.put(col, ruleEntry.getKey());
                    break;
                }
            }
        }
        // 文本列名→text_col_N 快速查表
        Map<String, String> textColMap = (textColumns != null && !textColumns.isEmpty())
                ? textColumns : Collections.emptyMap();

        List<LogDataRecord> batchList = new ArrayList<>();
        for (Map<String, Object> map : parsedData) {
            LogDataRecord record = new LogDataRecord();
            record.setFileId(fileId);
            Map<String, Object> extraMap = new HashMap<>();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String originalColName = entry.getKey().trim();
                String valStr = String.valueOf(entry.getValue());

                // 文本列：直接写入 text_col_N
                String textColName = textColMap.get(originalColName);
                if (textColName != null) {
                    setTextColumnValue(record, textColName, valStr);
                    continue;
                }

                BigDecimal val = null;
                try {
                    val = new BigDecimal(valStr);
                } catch (Exception e) {
                    extraMap.put(originalColName, valStr);
                    continue;
                }

                // O(1) 查表
                String finalKey = columnKeyMap.get(originalColName);
                if (finalKey == null) {
                    extraMap.put(originalColName, val);
                    continue;
                }

                switch (finalKey) {
                    case "depth": record.setDepth(val); break;
                    case "ac": record.setAc(val); break;
                    case "den": record.setDen(val); break;
                    case "gr": record.setGr(val); break;
                    case "rt": record.setRt(val); break;
                    case "sp": record.setSp(val); break;
                    default: extraMap.put(finalKey, val); break;
                }
            }
            if (!extraMap.isEmpty()) {
                record.setExtraJson(extraMap);
            }
            batchList.add(record);

            if (batchList.size() >= 2000) {
                batchInsert(batchList);
                batchList.clear();
            }
        }
        if (!batchList.isEmpty()) {
            batchInsert(batchList);
        }
    }

    private void setTextColumnValue(LogDataRecord record, String textColName, String value) {
        switch (textColName) {
            case "text_col_1": record.setTextCol1(value); break;
            case "text_col_2": record.setTextCol2(value); break;
            case "text_col_3": record.setTextCol3(value); break;
            case "text_col_4": record.setTextCol4(value); break;
            case "text_col_5": record.setTextCol5(value); break;
            case "text_col_6": record.setTextCol6(value); break;
            case "text_col_7": record.setTextCol7(value); break;
            case "text_col_8": record.setTextCol8(value); break;
            case "text_col_9": record.setTextCol9(value); break;
            case "text_col_10": record.setTextCol10(value); break;
        }
    }
}
