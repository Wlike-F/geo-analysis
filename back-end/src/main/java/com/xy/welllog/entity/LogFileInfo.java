package com.xy.welllog.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("log_file_info")
public class LogFileInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fileName;
    private String columnsJson;
    private String textColumnsJson;  // 文本列配置 {"岩性":"text_col_1", "地层":"text_col_2"}
    private Integer totalRows;
    private Integer status;
    private Date createTime;
}