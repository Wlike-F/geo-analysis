package com.xy.welllog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutRequestDTO {

    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
