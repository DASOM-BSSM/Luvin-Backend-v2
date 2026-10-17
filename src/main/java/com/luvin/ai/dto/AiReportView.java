package com.luvin.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiReportView(
        UUID finalPartnerId,
        String narrative,
        List<AiHighlightView> highlights,
        String renderMode,
        /** 사용자 대변 AI(representative)의 강점 목록. 구버전 리포트는 빈 배열로 내려간다. */
        List<AiReportTraitView> representativeStrengths,
        /** 사용자 대변 AI(representative)의 약점 목록. 구버전 리포트는 빈 배열로 내려간다. */
        List<AiReportTraitView> representativeWeaknesses
) {
}
