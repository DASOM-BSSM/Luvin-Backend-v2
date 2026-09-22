package com.luvin.ai.client.dto;

import java.util.List;
import java.util.UUID;

public record EpisodeResponseDto(
        UUID versionId,
        List<MessageResponseDto> messages,
        Integer nextAfterSequence,
        Boolean hasMore,
        EpisodeTopicResponseDto topic
) {
}
