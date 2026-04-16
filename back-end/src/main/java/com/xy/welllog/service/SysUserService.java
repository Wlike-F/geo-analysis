package com.xy.welllog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xy.welllog.dto.AuthTokenDTO;
import com.xy.welllog.dto.LoginDTO;
import com.xy.welllog.dto.RegisterDTO;
import com.xy.welllog.entity.SysUser;

public interface SysUserService extends IService<SysUser> {

    /**
     * 用户注册
     * @param registerDTO 注册信息
     */
    void register(RegisterDTO registerDTO);

    /**
     * 用户登录
     * @param loginDTO 登录信息
     * @return access + refresh token
     */
    AuthTokenDTO login(LoginDTO loginDTO);

    /**
     * 刷新 token
     * @param refreshToken refresh token
     * @return 新的 access + refresh token
     */
    AuthTokenDTO refreshToken(String refreshToken);

    /**
     * 注销 refresh token
     * @param refreshToken refresh token
     */
    void logout(String refreshToken);
}
