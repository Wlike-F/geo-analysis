package com.xy.welllog.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xy.welllog.entity.LogFileInfo;
import com.xy.welllog.mapper.LogFileInfoMapper;
import com.xy.welllog.service.LogFileInfoService;
import org.springframework.stereotype.Service;

@Service
public class LogFileInfoServiceImpl extends ServiceImpl<LogFileInfoMapper, LogFileInfo> implements LogFileInfoService {
}