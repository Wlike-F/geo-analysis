package com.xy.welllog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xy.welllog.entity.SysOperationLog;
import com.xy.welllog.mapper.SysOperationLogMapper;
import com.xy.welllog.service.SysOperationLogService;
import com.xy.welllog.utils.JwtUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog> implements SysOperationLogService {

    @Override
    public void recordLog(String module, String description, Integer fileCount, Long lineCount, Long userId) {
        SysOperationLog log = new SysOperationLog();
        log.setModule(module);
        log.setDescription(description);
        log.setFileCount(fileCount != null ? fileCount : 0);
        log.setLineCount(lineCount != null ? lineCount : 0L);
        log.setUserId(userId != null ? userId : 1L);
    }

    @Override
    public Map<String, Object> getDashboardStats(Long currentUserId, boolean isAdmin) {
        Map<String, Object> result = new HashMap<>();
        String systemStatus = "正常运行";
        Map<String, Object> dbStats = null;
        
        // 1. Get Top aggregated stats
        try {
            Long queryUserId = isAdmin ? null : currentUserId;
            dbStats = this.baseMapper.getSystemStats(queryUserId);
            if (dbStats == null) {
                systemStatus = "数据库异常";
            }
        } catch (Exception e) {
            systemStatus = "数据库异常";
        }

        // 2. Check disk space
        try {
            File root = new File(".");
            long usableSpaceGb = root.getUsableSpace() / (1024 * 1024 * 1024);
            if (usableSpaceGb < 5 && "正常运行".equals(systemStatus)) {
                systemStatus = "磁盘告警";
            }
        } catch (Exception e) {
            if ("正常运行".equals(systemStatus)) {
                systemStatus = "状态未知";
            }
        }

        Map<String, Object> formattedStats = new HashMap<>();
        formattedStats.put("totalFiles", getValueAsLong(dbStats, "totalfiles"));
        formattedStats.put("totalLines", getValueAsLong(dbStats, "totallines"));
        formattedStats.put("totalExports", getValueAsLong(dbStats, "totalexports"));
        formattedStats.put("systemStatus", systemStatus);
        
        result.put("stats", formattedStats);
        
        // 3. Get latest 5 activity logs
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        if (!isAdmin && currentUserId != null) {
            wrapper.eq(SysOperationLog::getUserId, currentUserId);
        }
        wrapper.orderByDesc(SysOperationLog::getCreateTime).last("LIMIT 5");
        List<SysOperationLog> recentLogs = this.list(wrapper);
        result.put("recentLogs", recentLogs);
        
        return result;
    }

    private Long getValueAsLong(Map<String, Object> map, String targetKey) {
        if (map == null) return 0L;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey().replace("_", "").toLowerCase();
            if (key.equals(targetKey.toLowerCase())) {
                Object val = entry.getValue();
                if (val instanceof Number) {
                    return ((Number) val).longValue();
                }
                if (val != null) {
                    try {
                        return Long.parseLong(val.toString());
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                }
            }
        }
        return 0L;
    }
}
