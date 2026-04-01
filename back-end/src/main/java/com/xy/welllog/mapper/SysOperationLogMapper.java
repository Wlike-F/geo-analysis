package com.xy.welllog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xy.welllog.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {

    @Select("SELECT " +
            "COALESCE(SUM(CASE WHEN module IN ('文件加载', '文件扫描', '系统初始化') THEN file_count ELSE 0 END), 0) as totalFiles, " +
            "COALESCE(SUM(CASE WHEN module IN ('文件加载', '文件扫描', '系统初始化') THEN line_count ELSE 0 END), 0) as totalLines, " +
            "COALESCE(SUM(CASE WHEN module = '报表导出' THEN 1 ELSE 0 END), 0) as totalExports " +
            "FROM sys_operation_log")
    Map<String, Object> getSystemStats();
}
