package com.luvin.ai.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** GET /api/ai/seasons 목록 한 항목. seasonNumber 오름차순으로 반환한다. */
public record AiSeasonSummaryView(
        UUID seasonId,
        int seasonNumber,
        String status,
        Integer currentEpisode,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        /** legacy | blocks — AiSeasonStatusView.episodeFormat과 같다. */
        String episodeFormat
) {
}
