package com.xy.welllog.dto;
import lombok.Data;
@Data
public class FilterRule {
    private String column;
    private String operator;
    private String value;
}
