package com.xy.welllog.dto;

import lombok.Data;
import java.util.List;

@Data
public class ConfirmUploadDTO {
    private String tempFilePath;
    private String originalFileName;
    private List<String> confirmedMapping; 
}
