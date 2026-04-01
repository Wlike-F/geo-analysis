package com.xy.welllog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_instruction")
public class SysInstruction extends BaseEntity {
    private String title;
    private String icon;
    private String content;
    private Integer sortOrder;
}
