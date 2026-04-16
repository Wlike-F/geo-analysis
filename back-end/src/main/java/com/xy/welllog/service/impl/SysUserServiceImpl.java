package com.xy.welllog.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xy.welllog.common.ResultCode;
import com.xy.welllog.dto.AuthTokenDTO;
import com.xy.welllog.dto.LoginDTO;
import com.xy.welllog.dto.RegisterDTO;
import com.xy.welllog.entity.SysRefreshToken;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.exception.BusinessException;
import com.xy.welllog.mapper.SysUserMapper;
import com.xy.welllog.service.SysRefreshTokenService;
import com.xy.welllog.service.SysUserService;
import com.xy.welllog.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private SysRefreshTokenService sysRefreshTokenService;

    private static final String SALT = "xwlog#2026";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void register(RegisterDTO registerDTO) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, registerDTO.getUsername());
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "用户名已存在");
        }

        SysUser sysUser = new SysUser();
        sysUser.setUsername(registerDTO.getUsername());
        sysUser.setEmail(registerDTO.getEmail());
        sysUser.setRole("user");
        sysUser.setRealName("新用户_" + System.currentTimeMillis() % 10000);

        String encryptPassword = DigestUtil.md5Hex(registerDTO.getPassword() + SALT);
        sysUser.setPassword(encryptPassword);

        this.save(sysUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenDTO login(LoginDTO loginDTO) {
        SysUser sysUser = getUserByUsername(loginDTO.getUsername());

        if (sysUser == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "用户名或密码错误");
        }

        String encryptPassword = DigestUtil.md5Hex(loginDTO.getPassword() + SALT);
        if (!StrUtil.equals(encryptPassword, sysUser.getPassword())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "用户名或密码错误");
        }

        return issueTokenPair(sysUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenDTO refreshToken(String refreshToken) {
        SysRefreshToken currentToken = getValidRefreshToken(refreshToken);
        SysUser sysUser = this.getById(currentToken.getUserId());
        if (sysUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }

        AuthTokenDTO newTokenPair = issueTokenPair(sysUser);
        SysRefreshToken nextToken = getValidRefreshToken(newTokenPair.getRefreshToken());

        currentToken.setRevoked(1);
        currentToken.setReplacedByTokenId(nextToken.getId());
        sysRefreshTokenService.updateById(currentToken);

        return newTokenPair;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(String refreshToken) {
        String tokenHash = DigestUtil.sha256Hex(refreshToken);
        LambdaQueryWrapper<SysRefreshToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRefreshToken::getTokenHash, tokenHash);
        SysRefreshToken token = sysRefreshTokenService.getOne(wrapper);
        if (token != null && !Objects.equals(token.getRevoked(), 1)) {
            token.setRevoked(1);
            sysRefreshTokenService.updateById(token);
        }
    }

    private AuthTokenDTO issueTokenPair(SysUser user) {
        String accessToken = jwtUtils.generateAccessToken(user.getUsername(), user.getId());
        String refreshToken = generateRefreshToken();

        SysRefreshToken refreshTokenRecord = new SysRefreshToken();
        refreshTokenRecord.setUserId(user.getId());
        refreshTokenRecord.setTokenHash(DigestUtil.sha256Hex(refreshToken));
        refreshTokenRecord.setExpiresAt(LocalDateTime.now().plusSeconds(jwtUtils.getRefreshExpirationSeconds()));
        refreshTokenRecord.setRevoked(0);
        sysRefreshTokenService.save(refreshTokenRecord);

        return new AuthTokenDTO(
                accessToken,
                refreshToken,
                "Bearer",
                jwtUtils.getAccessExpirationSeconds(),
                jwtUtils.getRefreshExpirationSeconds()
        );
    }

    private SysRefreshToken getValidRefreshToken(String refreshToken) {
        String tokenHash = DigestUtil.sha256Hex(refreshToken);
        LambdaQueryWrapper<SysRefreshToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRefreshToken::getTokenHash, tokenHash);
        SysRefreshToken token = sysRefreshTokenService.getOne(wrapper);

        if (token == null || Objects.equals(token.getRevoked(), 1) || token.getExpiresAt() == null
                || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        return token;
    }

    private SysUser getUserByUsername(String username) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getUsername, username);
        return this.getOne(queryWrapper);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
