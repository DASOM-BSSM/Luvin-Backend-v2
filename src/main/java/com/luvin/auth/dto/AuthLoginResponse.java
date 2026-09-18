package com.luvin.auth.dto;

public record AuthLoginResponse(
        Long userId,
        String nickname,
        boolean isNewUser,
        String accessToken
) {
}
