package com.luvin.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 문서용: 409 INSUFFICIENT_TOKENS 응답 본문(ApiResponse 형식). data에 필요한 토큰 수와 현재 잔액이 담긴다. */
@Schema(name = "AiInsufficientTokensError")
public record AiInsufficientTokensErrorView(
        boolean success,
        AiInsufficientTokensView data,
        String message,
        @Schema(allowableValues = {"INSUFFICIENT_TOKENS"}) String code
) {
}
