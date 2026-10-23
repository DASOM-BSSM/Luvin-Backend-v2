package com.luvin.ai.dto;

import java.util.UUID;

public record AiCharacterView(
        UUID characterId,
        String role,
        String gender,
        /** 장면 블록 회차 출연진 역할. 새 흐름을 시작하기 전이면 null. */
        String castRole,
        String attachmentStyle,
        String name
) {
}
