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
        UUID sourceSurveyResultId,
        /** 그 사용자의 몇 번째 시즌인지(1부터). */
        Integer seasonNumber,
        /** 항상 blocks(장면 블록 1~10화). 기존 5화(legacy) 시즌은 V10에서 지웠다 — 응답 호환을 위해 남긴 필드. */
        String episodeFormat,
        /** 장면 블록 회차(1~10화) 상태. 새 흐름을 시작하기 전이면 null. */
        com.luvin.ai.dto.story.AiStoryStateView story
) {
}
