package com.xy.welllog.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ConfirmUploadDTO {
    private String tempFilePath;
    private String originalFileName;
    private List<String> confirmedMapping;
    /** 文本列映射：原始列名 → text_col_N，如 {"岩性": "text_col_1"}，上传时指定则解析阶段直接写入物理列 */
    private Map<String, String> textColumns;
}
