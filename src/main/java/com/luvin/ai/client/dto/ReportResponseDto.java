package com.luvin.ai.client.dto;

import java.util.List;
import java.util.UUID;

/**
 * 5화 리포트 응답(2025 스키마 변경, PR #5). narrative/representative_strengths·weaknesses는
 * 폐기되고 tendency/feedback/strengths/weaknesses로 대체됐다. highlights는 배열에서
 * {episode2, episode4} 고정 키 객체로 바뀌었다. evidence/render_mode는 변경 없다.
 */
public record ReportResponseDto(
        UUID finalPartnerId,
        String tendency,
        String feedback,
        List<String> strengths,
        List<String> weaknesses,
        ReportHighlightsResponseDto highlights,
        List<UUID> evidence,
        String renderMode
) {
}
