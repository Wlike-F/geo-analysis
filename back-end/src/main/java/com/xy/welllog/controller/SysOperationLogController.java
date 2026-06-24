package com.xy.welllog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xy.welllog.common.Result;
import com.xy.welllog.config.InMemoryLogAppender;
import com.xy.welllog.entity.LogFileInfo;
import com.xy.welllog.entity.SysOperationLog;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.service.LogFileInfoService;
import com.xy.welllog.service.LogDataRecordService;
import com.xy.welllog.service.SysOperationLogService;
import com.xy.welllog.service.SysUserService;
import com.xy.welllog.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/sys/log")
public class SysOperationLogController {

    @Autowired
    private SysOperationLogService sysOperationLogService;
    
    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private LogFileInfoService logFileInfoService;

    @Autowired
    private LogDataRecordService logDataRecordService;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private InMemoryLogAppender inMemoryLogAppender;

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String username = jwtUtils.getUsernameFromToken(header.substring(7));
            if (username != null) {
                LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SysUser::getUsername, username);
                SysUser user = sysUserService.getOne(wrapper);
                if (user != null) {
                    return user.getId();
                }
            }
        }
        return null;
    }

    private String getUsername(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer "))
            return jwtUtils.getUsernameFromToken(header.substring(7));
        return null;
    }
    private boolean isAdmin(String username) {
        if (username == null) return false;
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        w.eq(SysUser::getUsername, username);
        SysUser user = sysUserService.getOne(w);
        return user != null && "admin".equals(user.getRole());
    }

    @GetMapping("/login")
    public Result<Page<SysOperationLog>> getLoginLogs(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {

        Long userId = getUserId(request);
        if (userId == null) {
            return Result.failed("用户未登录");
        }

        Page<SysOperationLog> pageParam = new Page<>(current, size);
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        // 获取当前用户的登录记录
        wrapper.eq(SysOperationLog::getUserId, userId)
               .eq(SysOperationLog::getModule, "用户登录")
               .orderByDesc(SysOperationLog::getCreateTime);

        Page<SysOperationLog> logPage = sysOperationLogService.page(pageParam, wrapper);
        return Result.success(logPage);
    }

    /**
     * 获取当前用户的所有操作日志（不限于登录）
     */
    @GetMapping("/all")
    public Result<Page<SysOperationLog>> getAllLogs(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {

        Long userId = getUserId(request);
        if (userId == null) {
            return Result.failed("用户未登录");
        }

        Page<SysOperationLog> pageParam = new Page<>(current, size);
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysOperationLog::getUserId, userId)
               .orderByDesc(SysOperationLog::getCreateTime);

        Page<SysOperationLog> logPage = sysOperationLogService.page(pageParam, wrapper);
        return Result.success(logPage);
    }

    /**
     * 获取当前用户的存储用量统计
     */
    @GetMapping("/storage")
    public Result<Map<String, Object>> getStorageStats(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return Result.failed("用户未登录");
        }

        Map<String, Object> stats = new LinkedHashMap<>();

        // 文件数
        long fileCount = logFileInfoService.count(new LambdaQueryWrapper<LogFileInfo>()
                .eq(LogFileInfo::getUserId, userId)
                .ne(LogFileInfo::getStatus, 2));
        stats.put("fileCount", fileCount);

        // 总解析行数
        Long totalLines = null;
        try {
            totalLines = jdbcTemplate.queryForObject(
                    "SELECT IFNULL(SUM(total_rows), 0) FROM log_file_info WHERE user_id = ? AND status != 2",
                    Long.class, userId);
        } catch (Exception e) {
            totalLines = 0L;
        }
        stats.put("totalLines", totalLines != null ? totalLines : 0L);

        // 脏数据行数
        Long dirtyLines = null;
        try {
            dirtyLines = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM log_dirty_data WHERE file_id IN (SELECT id FROM log_file_info WHERE user_id = ? AND status != 2)",
                    Long.class, userId);
        } catch (Exception e) {
            dirtyLines = 0L;
        }
        stats.put("dirtyLines", dirtyLines != null ? dirtyLines : 0L);

        // 操作日志总数
        long logCount = sysOperationLogService.count(new LambdaQueryWrapper<SysOperationLog>()
                .eq(SysOperationLog::getUserId, userId));
        stats.put("logCount", logCount);

        return Result.success(stats);
    }

    /**
     * 清除缓存：一键清理已删除文件的残留数据和过期日志（多线程并行）
     */
    @PostMapping("/cleanup")
    public Result<Map<String, Object>> cleanup(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return Result.failed("用户未登录");
        String username = getUsername(request);
        if (!isAdmin(username)) return Result.failed("无管理员权限");

        Map<String, Object> result = new ConcurrentHashMap<>();

        // 阶段1：先快速 COUNT 检查有没有数据，有才并行 DELETE
        CompletableFuture<Integer> delRecords = CompletableFuture.supplyAsync(() ->
            safeDelete(jdbcTemplate, "log_data_records",
                "SELECT COUNT(*) FROM log_data_records WHERE file_id IN (SELECT id FROM log_file_info WHERE status = 2)",
                "DELETE FROM log_data_records WHERE file_id IN (SELECT id FROM log_file_info WHERE status = 2)"));
        CompletableFuture<Integer> delDirty = CompletableFuture.supplyAsync(() ->
            safeDelete(jdbcTemplate, "log_dirty_data",
                "SELECT COUNT(*) FROM log_dirty_data WHERE file_id IN (SELECT id FROM log_file_info WHERE status = 2)",
                "DELETE FROM log_dirty_data WHERE file_id IN (SELECT id FROM log_file_info WHERE status = 2)"));
        CompletableFuture<Integer> delLogs = CompletableFuture.supplyAsync(() ->
            safeDelete(jdbcTemplate, "sys_operation_log",
                "SELECT COUNT(*) FROM sys_operation_log WHERE create_time < DATE_SUB(NOW(), INTERVAL 30 DAY)",
                "DELETE FROM sys_operation_log WHERE create_time < DATE_SUB(NOW(), INTERVAL 30 DAY)"));
        CompletableFuture<Integer> delTokens = CompletableFuture.supplyAsync(() ->
            safeDelete(jdbcTemplate, "sys_refresh_token",
                "SELECT COUNT(*) FROM sys_refresh_token WHERE revoked = 1 OR expires_at < NOW()",
                "DELETE FROM sys_refresh_token WHERE revoked = 1 OR expires_at < NOW()"));

        // 阶段2：等待子表数据清完后，删除文件元数据
        CompletableFuture<Void> delFiles = CompletableFuture.allOf(delRecords, delDirty)
            .thenRunAsync(() -> {
                int cnt = safeDelete(jdbcTemplate, "log_file_info",
                    "SELECT COUNT(*) FROM log_file_info WHERE status = 2",
                    "DELETE FROM log_file_info WHERE status = 2");
                result.put("deletedFiles", cnt);
            });

        // 收集结果
        try {
            result.put("deletedRecords", delRecords.get(300, TimeUnit.SECONDS));
            result.put("deletedDirty", delDirty.get(300, TimeUnit.SECONDS));
            result.put("deletedLogs", delLogs.get(300, TimeUnit.SECONDS));
            result.put("deletedTokens", delTokens.get(300, TimeUnit.SECONDS));
            delFiles.get(300, TimeUnit.SECONDS);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = e instanceof InterruptedException ? "操作被中断" : e.getClass().getSimpleName();
            result.put("error", msg);
        }

        sysOperationLogService.recordLog("系统管理", "执行缓存清理", 0, 0L, userId);
        return Result.success(result, "缓存清理完成");
    }

    /** 分批 DELETE：无 COUNT 预扫，直接删，每批 50000 行，删完即停 */
    private int safeDelete(JdbcTemplate jt, String tableName, String countSql, String deleteSql) {
        String batchSql = deleteSql + " LIMIT 50000";
        int total = 0, batch;
        do {
            batch = jt.update(batchSql);
            total += batch;
        } while (batch >= 50000);
        if (total > 0) log.info("[清理] {} 已删除 {} 条", tableName, total);
        return total;
    }

    /**
     * 获取后端运行日志（内存环形缓冲，最近 500 条）
     */
    @GetMapping("/runtime")
    public Result<List<InMemoryLogAppender.LogEntry>> getRuntimeLogs(
            @RequestParam(defaultValue = "ALL") String level,
            @RequestParam(defaultValue = "200") Integer lines,
            HttpServletRequest request) {

        Long userId = getUserId(request);
        if (userId == null) return Result.failed("用户未登录");
        if (!isAdmin(getUsername(request))) return Result.failed("无管理员权限");

        List<InMemoryLogAppender.LogEntry> entries = inMemoryLogAppender.getEntries(level, Math.min(lines, 500));
        return Result.success(entries);
    }
}
