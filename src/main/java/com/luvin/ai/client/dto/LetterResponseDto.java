package com.luvin.ai.client.dto;

import java.util.UUID;

public record LetterResponseDto(
        UUID letterId,
        UUID finalPartnerId,
        String body,
        String renderMode
) {
}
