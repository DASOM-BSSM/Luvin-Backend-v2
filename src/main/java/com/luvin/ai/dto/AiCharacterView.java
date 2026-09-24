package com.luvin.ai.dto;

import java.util.UUID;

public record AiCharacterView(
        UUID characterId,
        String role,
        String gender
) {
}
