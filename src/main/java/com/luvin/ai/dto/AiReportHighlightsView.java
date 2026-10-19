package com.luvin.ai.dto;

/** episode2/episode4 키는 계약상 항상 둘 다 온다(2025 리포트 스키마 변경, PR #5). */
public record AiReportHighlightsView(
        AiHighlightView episode2,
        AiHighlightView episode4
) {
}
