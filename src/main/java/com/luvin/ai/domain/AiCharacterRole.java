package com.luvin.ai.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.luvin.ai.client.exception.AiContractViolationException;

/**
 * role=representative인 캐릭터 ID를 사용자 대변 AI로 식별한다 (요구사항 5.1).
 * representative가 아닌 나머지는 모두 candidate로 취급한다.
 * SUPPORTING은 AI 시즌에는 없고 Spring이 장면 블록 회차용으로 추가한 출연진(친구, 분위기 메이커)이다.
 */
public enum AiCharacterRole {
    REPRESENTATIVE,
    CANDIDATE,
    SUPPORTING;

    /** 앱 응답에 AI wire 그대로 소문자("representative"/"candidate")로 노출한다. */
    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }

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
