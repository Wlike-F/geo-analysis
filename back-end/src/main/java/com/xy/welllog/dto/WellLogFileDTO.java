package com.xy.welllog.dto;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class WellLogFileDTO {
    private Long fileId;
    private String title;
    private List<String> columns;
    private List<Map<String, Object>> data;
    private Integer totalRows;
}
