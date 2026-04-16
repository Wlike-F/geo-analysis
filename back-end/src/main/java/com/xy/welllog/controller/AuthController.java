package com.xy.welllog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xy.welllog.common.Result;
import com.xy.welllog.dto.AuthTokenDTO;
import com.xy.welllog.dto.LoginDTO;
import com.xy.welllog.dto.LogoutRequestDTO;
import com.xy.welllog.dto.RefreshTokenRequestDTO;
import com.xy.welllog.dto.RegisterDTO;
import com.xy.welllog.entity.SysUser;
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
    public Result<AuthTokenDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        AuthTokenDTO token = sysUserService.login(loginDTO);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, loginDTO.getUsername());
        SysUser user = sysUserService.getOne(wrapper);
        operationLogService.recordLog("用户登录", "用户 [" + loginDTO.getUsername() + "] 登录系统", 0, 0L, user != null ? user.getId() : null);
        return Result.success(token, "登录成功");
    }

    @PostMapping("/refresh")
    public Result<AuthTokenDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO requestDTO) {
        AuthTokenDTO token = sysUserService.refreshToken(requestDTO.getRefreshToken());
        return Result.success(token, "刷新成功");
    }

    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody LogoutRequestDTO requestDTO) {
        sysUserService.logout(requestDTO.getRefreshToken());
        return Result.success(null, "退出成功");
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO registerDTO) {
        sysUserService.register(registerDTO);
        operationLogService.recordLog("用户注册", "新用户 [" + registerDTO.getUsername() + "] 注册账号", 0, 0L, null);
        return Result.success(null, "注册成功");
    }
}
