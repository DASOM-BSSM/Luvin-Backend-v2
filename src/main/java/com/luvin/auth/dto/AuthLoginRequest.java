package com.luvin.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank(message = "구글 access token 또는 auth code는 필수입니다.")
        String googleToken,
        @NotBlank(message = "googleId는 필수입니다.")
        String googleId,
        @NotBlank(message = "email은 필수입니다.")
        String email,
        @NotBlank(message = "nickname은 필수입니다.")
        String nickname
) {
}