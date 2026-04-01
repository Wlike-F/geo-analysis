package com.xy.welllog.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xy.welllog.common.Result;
import com.xy.welllog.entity.SysInstruction;
import com.xy.welllog.service.SysInstructionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/instruction")
public class SysInstructionController {

    @Autowired
    private SysInstructionService sysInstructionService;

    @GetMapping("/list")
    public Result<List<SysInstruction>> list() {
        QueryWrapper<SysInstruction> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort_order");
        List<SysInstruction> list = sysInstructionService.list(queryWrapper);
        return Result.success(list);
    }
}
