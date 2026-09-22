package com.luvin.ai.client.dto;

import java.util.UUID;

/**
 * sceneKind는 group|candidates_only|one_to_one 중 하나여야 한다 (요구사항 5.6).
 * 원문 문자열로 받고 서비스 계층에서 검증한다.
 */
public record MessageResponseDto(
        UUID messageId,
        Integer sequence,
        String sceneKind,
        UUID speakerId,
        String text
) {
}
