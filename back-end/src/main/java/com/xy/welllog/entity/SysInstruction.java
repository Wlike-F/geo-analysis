package com.xy.welllog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_instruction")
public class SysInstruction extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String icon;
    private String content;
    private Integer sortOrder;
}
