package com.luvin.common.security;

public record AuthenticatedUser(
        Long userId,
        String email,
        String nickname
) {
}
