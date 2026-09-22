package com.luvin.ai.client.dto;

/**
 * 시즌 생성 시 전송하는 대표 캐릭터(사용자 대변 AI) 프로필.
 * gender: male|female, adultAge: 19..120, personality: 1..1000자 (요구사항 3.3).
 */
public record CharacterProfileDto(
        String gender,
        Integer adultAge,
        String personality,
        TraitsDto traits
) {
}
