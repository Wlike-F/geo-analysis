package com.xy.welllog.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xy.welllog.entity.LogDataRecord;
import com.xy.welllog.entity.SysColumnMapping;
import com.xy.welllog.mapper.LogDataRecordMapper;
import com.xy.welllog.service.LogDataRecordService;
import com.xy.welllog.service.SysColumnMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class LogDataRecordServiceImpl extends ServiceImpl<LogDataRecordMapper, LogDataRecord> implements LogDataRecordService {

    @Autowired
    private SysColumnMappingService mappingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processAndSaveBatch(Long fileId, List<String> columns, List<Map<String, Object>> parsedData) {
        // 加载字典规则并编译为正则表达式
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

        List<LogDataRecord> batchList = new ArrayList<>();
        for (Map<String, Object> map : parsedData) {
            LogDataRecord record = new LogDataRecord();
            record.setFileId(fileId);
            Map<String, Object> extraMap = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String originalColName = entry.getKey().trim();
                String valStr = String.valueOf(entry.getValue());
                BigDecimal val = null;
                try {
                    val = new BigDecimal(valStr);
                } catch (Exception e) {
                    extraMap.put(originalColName, valStr);
                    continue;
                }
                
                String finalKey = null;
                for (Map.Entry<String, Pattern> ruleEntry : compiledRules.entrySet()) {
                    if (ruleEntry.getValue().matcher(originalColName).matches()) {
                        finalKey = ruleEntry.getKey();
                        break;
                    }
                }

                if (finalKey == null) {
                    extraMap.put(originalColName, val != null ? val : valStr);
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
            
            if (batchList.size() >= 5000) {
                this.saveBatch(batchList);
                batchList.clear();
            }
        }
        if (!batchList.isEmpty()) {
            this.saveBatch(batchList);
        }
    }
}