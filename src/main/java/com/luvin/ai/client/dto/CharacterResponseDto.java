package com.luvin.ai.client.dto;

import java.util.UUID;

public record CharacterResponseDto(
        UUID id,
        String role,
        String gender,
        Integer adultAge,
        String personality
) {
}
