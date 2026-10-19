package com.luvin.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiEpisodeMessagesView(
        Integer episodeNumber,
        UUID versionId,
        AiTopicView topic,
        List<AiMessageView> messages,
        Boolean hasMore,
        Integer nextAfterSequence,
        /** 이 회차에 이미 작성된 "나의 빵에게" 피드백. 아직 안 썼으면 null. */
        AiEpisodeFeedbackView feedback,
        /** 좋아요(하트) 누른 메시지 id 목록. 없으면 빈 배열. */
        List<UUID> likedMessageIds
) {
}
