package com.xy.welllog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xy.welllog.common.Result;
import com.xy.welllog.entity.SysColumnMapping;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.service.SysColumnMappingService;
import com.xy.welllog.service.SysOperationLogService;
import com.xy.welllog.service.SysUserService;
import com.xy.welllog.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/column-mapping")
public class SysColumnMappingController {

    @Autowired
    private SysColumnMappingService sysColumnMappingService;

    @Autowired
    private SysOperationLogService operationLogService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private JwtUtils jwtUtils;

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String username = jwtUtils.getUsernameFromToken(header.substring(7));
            if (username != null) {
                LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SysUser::getUsername, username);
                SysUser user = sysUserService.getOne(wrapper);
                if (user != null) {
                    return user.getId();
                }
            }
        }
        return null;
    }

    private String getMappingLabel(SysColumnMapping mapping) {
        if (mapping == null) {
            return "未知映射";
        }
        if (StringUtils.hasText(mapping.getStandardKey())) {
            return mapping.getStandardKey();
        }
        if (StringUtils.hasText(mapping.getStandardName())) {
            return mapping.getStandardName();
        }
        return String.valueOf(mapping.getId());
    }

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
    public Result<?> add(@RequestBody SysColumnMapping mapping, HttpServletRequest request) {
        boolean saved = sysColumnMappingService.save(mapping);
        if (saved) {
            operationLogService.recordLog("字段映射", "新增字段映射 [" + getMappingLabel(mapping) + "]", 0, 0L, getUserId(request));
            log.info("[字典] 新增字段映射: {}", getMappingLabel(mapping));
        }
        return Result.success(saved);
    }

    @PutMapping
    public Result<?> update(@RequestBody SysColumnMapping mapping, HttpServletRequest request) {
        boolean updated = sysColumnMappingService.updateById(mapping);
        if (updated) {
            operationLogService.recordLog("字段映射", "更新字段映射 [" + getMappingLabel(mapping) + "]", 0, 0L, getUserId(request));
            log.info("[字典] 更新字段映射: {}", getMappingLabel(mapping));
        }
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest request) {
        SysColumnMapping mapping = sysColumnMappingService.getById(id);
        if (mapping != null && mapping.getIsCore() != null && mapping.getIsCore() == 1) {
            log.warn("[字典] 尝试删除核心列被拒绝: id={}", id);
            return Result.failed("核心列禁止删除");
        }
        boolean removed = sysColumnMappingService.removeById(id);
        if (removed) {
            operationLogService.recordLog("字段映射", "删除字段映射 [" + getMappingLabel(mapping) + "]", 0, 0L, getUserId(request));
            log.info("[字典] 删除字段映射: {}", getMappingLabel(mapping));
        }
        return Result.success(removed);
    }

    @PostMapping("/batchDelete")
    public Result<?> batchDelete(@RequestBody Long[] ids, HttpServletRequest request) {
        if (ids == null || ids.length == 0) return Result.failed("请选择要删除的记录");
        List<SysColumnMapping> list = sysColumnMappingService.listByIds(Arrays.asList(ids));
        boolean hasCore = list.stream().anyMatch(item -> item.getIsCore() != null && item.getIsCore() == 1);
        if (hasCore) {
            return Result.failed("选中的记录中包含核心列，禁止删除!");
        }
        boolean removed = sysColumnMappingService.removeByIds(Arrays.asList(ids));
        if (removed) {
            operationLogService.recordLog("字段映射", "批量删除字段映射", ids.length, 0L, getUserId(request));
        }
        return Result.success(removed);
    }
}
