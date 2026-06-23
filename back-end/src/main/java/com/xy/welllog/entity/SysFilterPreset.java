package com.xy.welllog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 筛选条件配置表
 * 用 type 字段区分两类配置：
 *  - default_columns: 用户固定的"默认全局筛选列"清单（每用户最多1条，name 固定为 __default__）
 *  - preset:          用户保存的"条件预设模板"（每用户N条，可命名）
 */
@Data
@TableName("sys_filter_preset")
public class SysFilterPreset {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 配置类型: default_columns / preset */
    private String type;

    /** 名称（default_columns 固定 __default__；preset 为用户命名） */
    private String name;

    /** 列清单 JSON: ["GR","AC","SP"] */
    private String columnsJson;

    /** 数值条件 JSON: {"GR":{"min":"120","max":""}} */
    private String filtersJson;

    /** 文本条件 JSON: {"岩性":["砂岩","泥岩"]} */
    private String textFiltersJson;

    /** preset 专用: all全局 / current单文件 */
    private String scope;

    private Integer sortOrder;

    private Date createTime;

    private Date updateTime;
}
