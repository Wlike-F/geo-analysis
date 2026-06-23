package com.xy.welllog.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xy.welllog.entity.LogDataRecord;
import io.micrometer.common.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public interface LogDataRecordService extends IService<LogDataRecord> {
    /**
     * 批量入库（使用预编译的字典规则，避免每批重复查库）
     *
     * @param compiledRules key=标准列名, value=编译好的正则
     */
    void processAndSaveBatch(Long fileId, List<String> columns,
                             List<Map<String, Object>> parsedData,
                             Map<String, Pattern> compiledRules,
                             @Nullable Map<String, String> textColumns);
}