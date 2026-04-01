package com.xy.welllog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xy.welllog.entity.SysOperationLog;

import java.util.Map;

public interface SysOperationLogService extends IService<SysOperationLog> {
    
    void recordLog(String module, String description, Integer fileCount, Long lineCount, Long userId);
    
    Map<String, Object> getDashboardStats(Long currentUserId, boolean isAdmin);
}
