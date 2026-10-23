package com.luvin.ai.dto.story;

import java.util.UUID;

/**
 * 블록 참여 빵. 프론트는 내부 타입(InfernoParticipant)으로 변환해서 쓴다.
 * role은 출연진 역할(my_bread | avoidant | anxious | secure | friend | mood_maker),
 * stageRole은 이 배경에서의 역할(예: "같은 반 반장", 1화 무대 소개 전이면 null)이다.
 */
public record AiStoryParticipantView(UUID breadId, String name, String role, String attachmentStyle, boolean isMe,
                                     String breadType, String stageRole) {
}
