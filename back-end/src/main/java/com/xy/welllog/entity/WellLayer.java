package com.xy.welllog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("well_layer")
public class WellLayer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    private String layerName;

    private BigDecimal topDepth;

    private BigDecimal bottomDepth;

    private String remark;

    private Date createTime;
}
