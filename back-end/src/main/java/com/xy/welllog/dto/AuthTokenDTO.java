package com.xy.welllog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthTokenDTO {

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private Long accessExpiresIn;

    private Long refreshExpiresIn;
}
