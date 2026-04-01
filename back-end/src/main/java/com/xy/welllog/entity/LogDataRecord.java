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
}