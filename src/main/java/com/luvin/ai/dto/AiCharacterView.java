package com.luvin.ai.dto;

import java.util.UUID;

public record AiCharacterView(
        UUID characterId,
        String role,
        String gender,
        /** 장면 블록 회차 출연진 역할. 새 흐름을 시작하기 전이면 null. */
        String castRole,
        String attachmentStyle,
        String name,
        /**
         * 출연진 소개 한 줄(25자 이내). 1화 무대 소개 때 정해지고 시즌 동안 같다. 내 빵이거나 1화를 아직 생성하지
         * 않았으면 null.
         */
        String description
) {
}
