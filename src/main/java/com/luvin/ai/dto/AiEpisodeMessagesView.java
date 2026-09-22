package com.luvin.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiEpisodeMessagesView(
        Integer episodeNumber,
        UUID versionId,
        AiTopicView topic,
        List<AiMessageView> messages,
        Boolean hasMore,
        Integer nextAfterSequence
) {
}
