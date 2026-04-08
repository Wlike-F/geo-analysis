package com.xy.welllog.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xy.welllog.common.Result;
import com.xy.welllog.entity.SysColumnMapping;
import com.xy.welllog.service.SysColumnMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/column-mapping")
public class SysColumnMappingController {

    @Autowired
    private SysColumnMappingService sysColumnMappingService;

    @GetMapping("/page")
    public Result<?> getPage(@RequestParam(defaultValue = "1") Integer pageNum,
                             @RequestParam(defaultValue = "10") Integer pageSize,
                             @RequestParam(required = false) String keyword) {
        Page<SysColumnMapping> page = new Page<>(pageNum, pageSize);
        QueryWrapper<SysColumnMapping> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            queryWrapper.like("standard_key", keyword)
                    .or().like("standard_name", keyword)
                    .or().like("chinese_meaning", keyword);
        }
        queryWrapper.orderByDesc("is_core").orderByAsc("id");
        return Result.success(sysColumnMappingService.page(page, queryWrapper));
    }

    @GetMapping("/list")
    public Result<?> getList() {
        return Result.success(sysColumnMappingService.list());
    }

    @PostMapping
    public Result<?> add(@RequestBody SysColumnMapping mapping) {
        return Result.success(sysColumnMappingService.save(mapping));
    }

    @PutMapping
    public Result<?> update(@RequestBody SysColumnMapping mapping) {
        return Result.success(sysColumnMappingService.updateById(mapping));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        SysColumnMapping mapping = sysColumnMappingService.getById(id);
        if (mapping != null && mapping.getIsCore() != null && mapping.getIsCore() == 1) {
            return Result.failed("核心列禁止删除");
        }
        return Result.success(sysColumnMappingService.removeById(id));
    }
    
    @PostMapping("/batchDelete")
    public Result<?> batchDelete(@RequestBody Long[] ids) {
        List<SysColumnMapping> list = sysColumnMappingService.listByIds(Arrays.asList(ids));
        boolean hasCore = list.stream().anyMatch(item -> item.getIsCore() != null && item.getIsCore() == 1);
        if(hasCore) {
            return Result.failed("选中的记录中包含核心列，禁止删除!");
        }
        return Result.success(sysColumnMappingService.removeByIds(Arrays.asList(ids)));
    }
}
