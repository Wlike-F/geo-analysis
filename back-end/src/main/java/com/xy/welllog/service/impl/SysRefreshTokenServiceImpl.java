package com.xy.welllog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xy.welllog.entity.SysRefreshToken;
import com.xy.welllog.mapper.SysRefreshTokenMapper;
import com.xy.welllog.service.SysRefreshTokenService;
import org.springframework.stereotype.Service;

@Service
public class SysRefreshTokenServiceImpl extends ServiceImpl<SysRefreshTokenMapper, SysRefreshToken> implements SysRefreshTokenService {
}
