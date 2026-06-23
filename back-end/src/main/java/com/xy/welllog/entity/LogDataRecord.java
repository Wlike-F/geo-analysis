package com.xy.welllog.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

@Data
@TableName(value = "log_data_records", autoResultMap = true)
public class LogDataRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fileId;
    private BigDecimal depth;
    private BigDecimal ac;
    private BigDecimal den;
    private BigDecimal gr;
    private BigDecimal sp;
    private BigDecimal rt;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraJson;

    // 文本预留列（岩性、地层名等分类字段）
    // select=false: 不参与自动 SELECT（兼容未执行 ALTER TABLE 的旧数据库）
    @TableField(select = false)
    private String textCol1;
    @TableField(select = false)
    private String textCol2;
    @TableField(select = false)
    private String textCol3;
    @TableField(select = false)
    private String textCol4;
    @TableField(select = false)
    private String textCol5;
    @TableField(select = false)
    private String textCol6;
    @TableField(select = false)
    private String textCol7;
    @TableField(select = false)
    private String textCol8;
    @TableField(select = false)
    private String textCol9;
    @TableField(select = false)
    private String textCol10;
}