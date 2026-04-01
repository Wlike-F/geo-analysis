package com.xy.welllog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("sys_column_mapping")
public class SysColumnMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String standardKey;

    private String standardName;

    private String chineseMeaning;

    private String aliasList;

    private Integer isCore;

    private Date createTime;

    private Date updateTime;
}
