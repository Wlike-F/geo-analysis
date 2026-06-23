package com.xy.welllog.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xy.welllog.common.Result;
import com.xy.welllog.entity.SysFilterPreset;
import com.xy.welllog.entity.SysUser;
import com.xy.welllog.service.SysFilterPresetService;
import com.xy.welllog.service.SysOperationLogService;
import com.xy.welllog.service.SysUserService;
import com.xy.welllog.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 筛选条件配置（默认全局筛选列 + 条件预设模板）
 * 所有接口自动按 JWT 取当前用户 user_id，实现按用户私有隔离。
 */
@Slf4j
@RestController
@RequestMapping("/filter-preset")
public class SysFilterPresetController {

    @Autowired
    private SysFilterPresetService sysFilterPresetService;

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
        return 1L;
    }

    // ============ 默认全局筛选列 ============

    /** 取我的默认列配置 */
    @GetMapping("/default-columns")
    public Result<SysFilterPreset> getDefaultColumns(HttpServletRequest request) {
        Long userId = getUserId(request);
        return Result.success(sysFilterPresetService.getDefaultColumns(userId));
    }

    /** 保存（新建或更新）我的默认列配置 */
    @PostMapping("/default-columns")
    public Result<SysFilterPreset> saveDefaultColumns(@RequestBody DefaultColumnsDTO dto, HttpServletRequest request) {
        Long userId = getUserId(request);
        SysFilterPreset saved = sysFilterPresetService.saveDefaultColumns(userId, dto.getColumnsJson());
        operationLogService.recordLog("筛选偏好", "更新默认全局筛选列", 0, 0L, userId);
        log.info("[筛选偏好] 用户{} 更新默认全局筛选列", userId);
        return Result.success(saved, "默认筛选列保存成功");
    }

    /** 默认列配置入参 DTO */
    @lombok.Data
    public static class DefaultColumnsDTO {
        private String columnsJson;
    }

    // ============ 条件预设模板 ============

    /** 列出我的所有预设模板 */
    @GetMapping("/preset")
    public Result<List<SysFilterPreset>> listPresets(HttpServletRequest request) {
        Long userId = getUserId(request);
        return Result.success(sysFilterPresetService.listPresets(userId));
    }

    /** 取单个预设详情 */
    @GetMapping("/preset/{id}")
    public Result<SysFilterPreset> getPreset(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        SysFilterPreset preset = sysFilterPresetService.getById(id);
        if (preset == null || !userId.equals(preset.getUserId())) {
            return Result.failed("预设不存在或无权访问");
        }
        return Result.success(preset);
    }

    /** 新建或更新一个预设模板 */
    @PostMapping("/preset")
    public Result<SysFilterPreset> savePreset(@RequestBody SysFilterPreset preset, HttpServletRequest request) {
        Long userId = getUserId(request);
        try {
            boolean isCreate = preset.getId() == null;
            SysFilterPreset saved = sysFilterPresetService.savePreset(userId, preset);
            operationLogService.recordLog("筛选偏好", (isCreate ? "新建" : "更新") + "条件预设 [" + saved.getName() + "]", 0, 0L, userId);
            log.info("[筛选偏好] 用户{} {}条件预设: {}", userId, isCreate ? "新建" : "更新", saved.getName());
            return Result.success(saved, isCreate ? "预设创建成功" : "预设更新成功");
        } catch (RuntimeException e) {
            return Result.failed(e.getMessage());
        }
    }

    /** 删除一个预设模板 */
    @DeleteMapping("/preset/{id}")
    public Result<Void> deletePreset(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        SysFilterPreset existing = sysFilterPresetService.getById(id);
        if (existing == null || !userId.equals(existing.getUserId())) {
            return Result.failed("预设不存在或无权删除");
        }
        boolean ok = sysFilterPresetService.deletePreset(userId, id);
        if (ok) {
            operationLogService.recordLog("筛选偏好", "删除条件预设 [" + existing.getName() + "]", 0, 0L, userId);
            log.info("[筛选偏好] 用户{} 删除条件预设: {}", userId, existing.getName());
            return Result.success(null, "删除成功");
        }
        return Result.failed("删除失败");
    }
}
