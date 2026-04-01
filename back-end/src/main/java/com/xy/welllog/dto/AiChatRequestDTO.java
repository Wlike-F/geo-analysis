package com.xy.welllog.dto;
import lombok.Data;
import java.util.List;
@Data
public class AiChatRequestDTO {
    private Long sessionId;
    private String prompt;
    private List<Long> fileIds;
}
