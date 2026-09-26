package com.luvin.ai.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.luvin.ai.client.exception.AiContractViolationException;

/**
 * AI 서비스가 내려주는 메시지의 장면 종류(요구사항 5.6). 값 자체는 AI 서비스 wire 그대로
 * snake_case 소문자("group"/"candidates_only"/"one_to_one")로 앱에 노출한다 — enum 이름을
 * 그대로 직렬화하지 않고 {@link #wireValue()}로 고정한다.
 */
public enum AiSceneKind {
    GROUP("group"),
    CANDIDATES_ONLY("candidates_only"),
    ONE_TO_ONE("one_to_one");

    private final String wireValue;

    AiSceneKind(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    public static AiSceneKind fromWire(String wireValue) {
        for (AiSceneKind kind : values()) {
            if (kind.wireValue.equals(wireValue)) {
                return kind;
            }
        }
        throw new AiContractViolationException("알 수 없는 scene_kind: " + wireValue, null);
    }
}
