package com.luvin.token.dto;

public record TokenUseResponse(
        int usedToken,
        int remainingToken,
        String message
) {
}