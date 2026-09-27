package com.luvin.ai.client.dto;

import java.util.UUID;

/** 5화 리포트의 하이라이트 메시지 하나. AI 서비스 wire: message_id, text. */
public record HighlightResponseDto(
        UUID messageId,
        String text
) {
}
