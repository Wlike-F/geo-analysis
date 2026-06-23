package com.xy.welllog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xy.welllog.entity.SysFilterPreset;
import com.xy.welllog.mapper.SysFilterPresetMapper;
import com.xy.welllog.service.SysFilterPresetService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysFilterPresetServiceImpl extends ServiceImpl<SysFilterPresetMapper, SysFilterPreset> implements SysFilterPresetService {

    /** default_columns 类型的固定名称，每用户唯一 */
    private static final String DEFAULT_COLUMNS_NAME = "__default__";

    @Override
    public SysFilterPreset getDefaultColumns(Long userId) {
        if (userId == null) return null;
        LambdaQueryWrapper<SysFilterPreset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysFilterPreset::getUserId, userId)
               .eq(SysFilterPreset::getType, "default_columns")
               .last("LIMIT 1");
        return this.getOne(wrapper);
    }

    @Override
    public SysFilterPreset saveDefaultColumns(Long userId, String columnsJson) {
        SysFilterPreset existing = getDefaultColumns(userId);
        if (existing != null) {
            existing.setColumnsJson(columnsJson);
            this.updateById(existing);
            return existing;
        }
        SysFilterPreset record = new SysFilterPreset();
        record.setUserId(userId);
        record.setType("default_columns");
        record.setName(DEFAULT_COLUMNS_NAME);
        record.setColumnsJson(columnsJson);
        this.save(record);
        return record;
    }

    @Override
    public List<SysFilterPreset> listPresets(Long userId) {
        LambdaQueryWrapper<SysFilterPreset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysFilterPreset::getUserId, userId)
               .eq(SysFilterPreset::getType, "preset")
               .orderByAsc(SysFilterPreset::getSortOrder)
               .orderByAsc(SysFilterPreset::getId);
        return this.list(wrapper);
    }

    @Override
    public SysFilterPreset savePreset(Long userId, SysFilterPreset preset) {
        // 强制归属当前用户，保证数据隔离
        preset.setUserId(userId);
        preset.setType("preset");
        if (preset.getId() != null) {
            // 更新：先校验这条记录属于当前用户
            SysFilterPreset existing = this.getById(preset.getId());
            if (existing == null || !userId.equals(existing.getUserId())) {
                throw new RuntimeException("无权修改该预设");
            }
            this.updateById(preset);
            return this.getById(preset.getId());
        }
        // 新建
        this.save(preset);
        return preset;
    }

    @Override
    public boolean deletePreset(Long userId, Long id) {
        SysFilterPreset existing = this.getById(id);
        if (existing == null || !userId.equals(existing.getUserId())) {
            return false;
        }
        return this.removeById(id);
    }
}
