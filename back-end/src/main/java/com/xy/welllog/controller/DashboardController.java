package com.xy.welllog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xy.welllog.common.Result;
import com.xy.welllog.entity.LogFileInfo;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.service.LogFileInfoService;
import com.xy.welllog.service.SysOperationLogService;
import com.xy.welllog.service.SysUserService;
import com.xy.welllog.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SysOperationLogService sysOperationLogService;
    private final SysUserService sysUserService;
    private final LogFileInfoService logFileInfoService;
    private final JwtUtils jwtUtils;

    private String getUsername(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return jwtUtils.getUsernameFromToken(header.substring(7));
        }
        return null;
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getDashboardStats(HttpServletRequest request) {
        String username = getUsername(request);
        boolean isAdmin = false;
        Long userId = null;

        if (username != null) {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUser::getUsername, username);
            SysUser user = sysUserService.getOne(wrapper);
            if (user != null) {
                userId = user.getId();
                isAdmin = "admin".equals(user.getRole());
            }
        }

        return Result.success(sysOperationLogService.getDashboardStats(userId, isAdmin));
    }

    @GetMapping("/recentFiles")
    public Result<?> getRecentFiles(HttpServletRequest request) {
        String username = getUsername(request);
        if (username == null) return Result.failed("用户未登录");

        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getUsername, username);
        SysUser user = sysUserService.getOne(userWrapper);
        if (user == null) return Result.failed("用户不存在");

        LambdaQueryWrapper<LogFileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogFileInfo::getUserId, user.getId())
               .ne(LogFileInfo::getStatus, 2)
               .orderByDesc(LogFileInfo::getCreateTime)
               .last("LIMIT 5");

        return Result.success(logFileInfoService.list(wrapper));
    }
}
