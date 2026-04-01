package com.xy.welllog.controller;

import com.xy.welllog.common.Result;
import com.xy.welllog.dto.LoginDTO;
import com.xy.welllog.dto.RegisterDTO;
import com.xy.welllog.service.SysOperationLogService;
import com.xy.welllog.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private SysUserService sysUserService;
    
    @Autowired
    private SysOperationLogService operationLogService;

    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody LoginDTO loginDTO) {        
        String token = sysUserService.login(loginDTO);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.xy.welllog.entity.SysUser> wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(com.xy.welllog.entity.SysUser::getUsername, loginDTO.getUsername());
        com.xy.welllog.entity.SysUser user = sysUserService.getOne(wrapper);
        operationLogService.recordLog("用户登录", "用户 [" + loginDTO.getUsername() + "] 登录系统", 0, 0L, user != null ? user.getId() : null);
        return Result.success(token, "登录成功");
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO registerDTO) {
        sysUserService.register(registerDTO);
        operationLogService.recordLog("用户注册", "新用户 [" + registerDTO.getUsername() + "] 注册账号", 0, 0L,null);
        return Result.success(null, "注册成功");
    }
}
