package com.xy.welllog.dto;
import lombok.Data;
import java.util.Map;
import java.math.BigDecimal;

@Data
public class LogDataQueryDTO {
    private Long fileId;
    private Integer current = 1;
    private Integer size = 100;
    private Map<String, FilterRange> filters;

    @Data
    public static class FilterRange {
        private BigDecimal min;
        private BigDecimal max;
    }
}
