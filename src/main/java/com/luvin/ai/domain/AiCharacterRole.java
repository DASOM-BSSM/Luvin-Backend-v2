package com.luvin.ai.domain;

import com.luvin.ai.client.exception.AiContractViolationException;

/**
 * role=representative인 캐릭터 ID를 사용자 대변 AI로 식별한다 (요구사항 5.1).
 * representative가 아닌 나머지는 모두 candidate로 취급한다.
 */
public enum AiCharacterRole {
    REPRESENTATIVE,
    CANDIDATE;

    public static AiCharacterRole fromWire(String wireValue, String rawBody) {
        if (wireValue == null) {
            throw new AiContractViolationException("character role이 null입니다.", rawBody);
        }
        return switch (wireValue.trim().toLowerCase()) {
            case "representative" -> REPRESENTATIVE;
            case "candidate" -> CANDIDATE;
            default -> throw new AiContractViolationException("알 수 없는 character role: " + wireValue, rawBody);
        };
    }
}
