package com.luvin.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank(message = "구글 ID 토큰(idToken)은 필수입니다.")
        String idToken
) {
}