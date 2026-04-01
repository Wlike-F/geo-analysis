package com.xy.welllog.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WellLogDataDTO {

    @JsonProperty("DEPTH")
    @ExcelProperty("深度 (DEPTH)")
    private String DEPTH;

    @JsonProperty("AC")
    @ExcelProperty("声波时差 (AC)")
    private String AC;

    @JsonProperty("DEN")
    @ExcelProperty("密度 (DEN)")
    private String DEN;

    @JsonProperty("GR")
    @ExcelProperty("自然伽马 (GR)")
    private String GR;

    @JsonProperty("SP")
    @ExcelProperty("自然电位 (SP)")
    private String SP;

    @JsonProperty("RT")
    @ExcelProperty("深测向电阻率 (RT)")
    private String RT;

}
