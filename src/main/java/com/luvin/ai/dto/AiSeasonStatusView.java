package com.luvin.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiSeasonStatusView(
        UUID seasonId,
        Integer revision,
        Integer currentEpisode,
        String status,
        List<AiCharacterView> characters
) {
}
