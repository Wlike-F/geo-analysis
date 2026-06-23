package com.xy.welllog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xy.welllog.entity.SysFilterPreset;

import java.util.List;

public interface SysFilterPresetService extends IService<SysFilterPreset> {

    /**
     * 获取用户的"默认全局筛选列"配置（type=default_columns）
     * @param userId 用户ID
     * @return 配置记录，不存在返回 null
     */
    SysFilterPreset getDefaultColumns(Long userId);

    /**
     * 保存（新建或更新）用户的"默认全局筛选列"配置（每用户唯一）
     * @param userId       用户ID
     * @param columnsJson  列清单 JSON 字符串
     * @return 保存后的记录
     */
    SysFilterPreset saveDefaultColumns(Long userId, String columnsJson);

    /**
     * 列出用户的所有条件预设模板（type=preset）
     * @param userId 用户ID
     * @return 预设列表，按 sortOrder、id 排序
     */
    List<SysFilterPreset> listPresets(Long userId);

    /**
     * 新建或更新一个条件预设模板（带 id 则更新，否则新建）
     * @param userId 用户ID（强制覆盖为当前用户，保证隔离）
     * @param preset 前端传入的预设
     * @return 保存后的记录
     */
    SysFilterPreset savePreset(Long userId, SysFilterPreset preset);

    /**
     * 删除一个条件预设模板（带用户隔离校验）
     * @param userId 用户ID
     * @param id     预设ID
     * @return 是否删除成功
     */
    boolean deletePreset(Long userId, Long id);
}
