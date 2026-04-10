package com.xy.welllog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xy.welllog.common.Result;
import com.xy.welllog.entity.SysOperationLog;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.service.SysOperationLogService;
import com.xy.welllog.service.SysUserService;
import com.xy.welllog.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sys/log")
public class SysOperationLogController {

    @Autowired
    private SysOperationLogService sysOperationLogService;
    
    @Autowired
    private SysUserService sysUserService;
    
    @Autowired
    private JwtUtils jwtUtils;

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
}
