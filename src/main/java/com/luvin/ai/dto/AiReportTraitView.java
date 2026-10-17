package com.luvin.ai.dto;

import java.util.List;
import java.util.UUID;

/** 사용자 대변 AI(representative)의 강점 또는 약점 항목 하나. */
public record AiReportTraitView(
        String title,
        String description,
        List<UUID> evidenceMessageIds
) {
}
