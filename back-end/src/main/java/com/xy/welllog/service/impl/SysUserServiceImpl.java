package com.xy.welllog.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xy.welllog.common.ResultCode;
import com.xy.welllog.dto.LoginDTO;
import com.xy.welllog.dto.RegisterDTO;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.exception.BusinessException;
import com.xy.welllog.mapper.SysUserMapper;
import com.xy.welllog.service.SysUserService;
import com.xy.welllog.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Autowired
    private JwtUtils jwtUtils;

    // 盐值，用于简单混淆密码
    private static final String SALT = "xwlog#2026";

    @Override
    public void register(RegisterDTO registerDTO) {
        // 1. 检查用户名是否存在
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, registerDTO.getUsername());
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "用户名已存在");
        }

        // 2. 构造用户对象
        SysUser sysUser = new SysUser();
        sysUser.setUsername(registerDTO.getUsername());
        sysUser.setEmail(registerDTO.getEmail());
        sysUser.setRole("user"); // 默认注册为普通用户
        sysUser.setRealName("新用户_" + System.currentTimeMillis() % 10000);
        
        // 3. 密码加密存储 (MD5 + Salt)
        String encryptPassword = DigestUtil.md5Hex(registerDTO.getPassword() + SALT);
        sysUser.setPassword(encryptPassword);

        // 4. 保存入库
        this.save(sysUser);
    }

    @Override
    public String login(LoginDTO loginDTO) {
        // 1. 根据用户名查询用户
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, loginDTO.getUsername());
        SysUser sysUser = this.getOne(queryWrapper);
        
        if (sysUser == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "用户名或密码错误");
        }

        // 2. 校验密码
        String encryptPassword = DigestUtil.md5Hex(loginDTO.getPassword() + SALT);
        if (!StrUtil.equals(encryptPassword, sysUser.getPassword())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "用户名或密码错误");
        }

        // 3. 生成 JWT Token
        return jwtUtils.generateToken(sysUser.getUsername());
    }
}
