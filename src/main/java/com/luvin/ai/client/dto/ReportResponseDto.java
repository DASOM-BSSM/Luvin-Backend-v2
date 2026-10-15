package com.luvin.ai.client.dto;

import java.util.List;
import java.util.UUID;

/**
 * representativeStrengths/representativeWeaknesses는 구버전 리포트에는 없을 수 있다(wire에 키
 * 자체가 없으면 Jackson이 null로 둔다) — 앱 응답으로 옮길 때 null이면 빈 배열로 처리한다
 * (AiSeasonOrchestrationServiceImpl.getReport 참고).
 */
public record ReportResponseDto(
        UUID finalPartnerId,
        String narrative,
        List<HighlightResponseDto> highlights,
        List<UUID> evidence,
        String renderMode,
        List<ReportTraitResponseDto> representativeStrengths,
        List<ReportTraitResponseDto> representativeWeaknesses
) {
}
