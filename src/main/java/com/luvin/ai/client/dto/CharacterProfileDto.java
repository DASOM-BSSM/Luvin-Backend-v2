package com.luvin.ai.client.dto;

/**
 * 시즌 생성 시 전송하는 대표 캐릭터(사용자 대변 AI) 프로필.
 * gender: male|female, traits: 13개 성향 1..100 (요구사항 3.3).
 */
public record CharacterProfileDto(
        String gender,
        TraitsDto traits
) {
}
