package com.luvin.ai.client.dto;

import java.util.List;
import java.util.UUID;

/**
 * 5화 리포트의 강점/약점 항목 하나(representative_strengths/representative_weaknesses 공통 shape).
 * AI 서비스 wire: title, description, evidence_message_ids.
 */
public record ReportTraitResponseDto(
        String title,
        String description,
        List<UUID> evidenceMessageIds
) {
}
