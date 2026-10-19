package com.luvin.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * 5화 최종 리포트(2025 스키마 변경, PR #5). narrative(자유 서술)는 tendency/feedback 두 문장으로,
 * representativeStrengths/Weaknesses(title/description/evidence 포함 객체)는 strengths/weaknesses
 * (각 1~3개, 20자 이내 태그 문자열)로 대체됐다. 점수/수치는 담지 않는다 — 심리 진단처럼
 * 표현하지 않는다.
 */
public record AiReportView(
        UUID finalPartnerId,
        /** 150자 이내 문장. */
        String tendency,
        /** 150자 이내 문장. */
        String feedback,
        /** 1~3개, 각 20자 이내 태그. */
        List<String> strengths,
        /** 1~3개, 각 20자 이내 태그. */
        List<String> weaknesses,
        AiReportHighlightsView highlights,
        String renderMode
) {
}
