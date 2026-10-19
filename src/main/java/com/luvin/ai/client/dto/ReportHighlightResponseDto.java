package com.luvin.ai.client.dto;

/** 하이라이트는 항상 사용자 대변 AI(representative)의 발화다 — 발화자 식별자가 더 이상 오지 않는다. */
public record ReportHighlightResponseDto(
        String message,
        String reason
) {
}
