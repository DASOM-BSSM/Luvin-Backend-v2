package com.luvin.ai.client.story;

import java.util.List;
import java.util.UUID;

/** 설계 단계가 정한 블록 하나. AI는 이 순서·참여 빵을 바꾸지 않고 대사만 쓴다. */
public record StoryBlockPlanDto(
        String id,
        String type,
        List<UUID> participantIds,
        /** messenger만: direct | group */
        String messengerKind,
        /** 장면의 상대 빵(혼자인 장면이면 null). */
        UUID focusBreadId,
        /** choice_result만: 회피형이 ②에서 짧게 수락하는 장면이면 true. */
        boolean shortReply
) {
}
