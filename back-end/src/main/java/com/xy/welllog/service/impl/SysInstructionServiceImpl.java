package com.xy.welllog.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xy.welllog.entity.SysInstruction;
import com.xy.welllog.mapper.SysInstructionMapper;
import com.xy.welllog.service.SysInstructionService;
import org.springframework.stereotype.Service;

@Service
public class SysInstructionServiceImpl extends ServiceImpl<SysInstructionMapper, SysInstruction> implements SysInstructionService {
}
