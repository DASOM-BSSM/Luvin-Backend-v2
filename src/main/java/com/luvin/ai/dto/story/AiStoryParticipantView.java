package com.luvin.ai.dto.story;

import java.util.UUID;

/**
 * 블록 참여 빵. 프론트 InfernoParticipant에 맞춘다 — 프론트 저장소의 inferno 타입 정의를 확인하면 필드명을 맞춘다.
 * role은 출연진 역할(my_bread | avoidant | anxious | secure | friend | mood_maker).
 */
public record AiStoryParticipantView(UUID breadId, String name, String role, String attachmentStyle, boolean isMe) {
}
