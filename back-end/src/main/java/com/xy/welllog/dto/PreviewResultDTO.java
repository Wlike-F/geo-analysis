package com.xy.welllog.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PreviewResultDTO {
    private String tempFilePath;
    private String originalFileName;
    private List<String> suggestedMapping; 
    private List<String> originalHeaders; 
    private List<Map<String, Object>> previewData; 
}
