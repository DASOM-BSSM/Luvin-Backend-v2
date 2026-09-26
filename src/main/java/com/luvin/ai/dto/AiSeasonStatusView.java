package com.luvin.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiSeasonStatusView(
        UUID seasonId,
        Integer revision,
        Integer currentEpisode,
        String status,
        List<AiCharacterView> characters,
        /** 이 시즌을 만든 survey_results.id. legacy(원시 traits) 경로로 만든 시즌은 null — 추측하지 않는다. */
        UUID sourceSurveyResultId
) {
}
