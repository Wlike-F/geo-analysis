package com.xy.welllog.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xy.welllog.entity.LogDataRecord;
import java.util.List;
import java.util.Map;

public interface LogDataRecordService extends IService<LogDataRecord> {
    void processAndSaveBatch(Long fileId, List<String> columns, List<Map<String, Object>> parsedData);
}