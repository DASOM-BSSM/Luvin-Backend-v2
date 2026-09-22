package com.luvin.ai.client.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SeasonResponseDto(
        UUID seasonId,
        Integer revision,
        Integer currentEpisode,
        List<CharacterResponseDto> characters,
        Map<String, UUID> activeVersionIds
) {
}
