package com.luvin.ai.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** 출연 빵의 애착 성향. 수락·거절 판정의 성향 보정에 쓴다. 내 빵/친구/분위기 메이커는 NONE. */
public enum AiAttachmentStyle {
    AVOIDANT,
    ANXIOUS,
    SECURE,
    NONE;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
