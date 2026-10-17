package com.luvin.ai.client.dto;

import java.util.UUID;

/**
 * 5화 리포트의 하이라이트 메시지 하나. AI 서비스 wire: message_id, speaker_id, speaker_role
 * (representative|candidate, AiCharacterRole로 검증 전까지는 raw 문자열로 받는다), text, reason,
 * observed_behavior.
 */
public record HighlightResponseDto(
        UUID messageId,
        UUID speakerId,
        String speakerRole,
        String text,
        String reason,
        String observedBehavior
) {
}
