package com.luvin.ai.dto;

/**
 * 시즌 생성 요청 시 Spring 내부에서 조립한 대표 캐릭터 프로필(앱→Spring 입력이 아니라
 * Spring이 자체 계산한 13개 성향 점수를 담아 이 서비스에 넘기는 내부 DTO).
 */
public record AiCharacterProfileRequest(
        String gender,
        AiTraitsRequest traits
) {
}
