package com.luvin.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** GET /api/ai/seasons 목록 한 항목. seasonNumber 내림차순(최신 시즌부터)으로 반환한다. */
public record AiSeasonSummaryView(
        UUID seasonId,
        int seasonNumber,
        String status,
        Integer currentEpisode,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        /** legacy | blocks — AiSeasonStatusView.episodeFormat과 같다. */
        String episodeFormat,
        /** 시즌 배경(school | campus | cafe | office). 회차 흐름을 시작하기 전이면 null. */
        @Schema(types = {"string", "null"}, allowableValues = {"school", "campus", "office", "cafe"}) String setting,
        /** 카드 제목(예: "In School"). setting이 없으면 null. */
        @Schema(types = {"string", "null"}) String settingTitle
) {
}
