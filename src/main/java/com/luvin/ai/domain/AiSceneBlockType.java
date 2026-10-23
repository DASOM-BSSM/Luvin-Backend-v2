package com.luvin.ai.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** 장면 블록 종류. 프론트 계약 문자열(소문자 snake_case) 그대로 내려간다. */
public enum AiSceneBlockType {
    TRANSITION,
    GROUP,
    PAIR,
    MESSENGER,
    CHOICE,
    CHOICE_RESULT,
    INTERVIEW;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }

    /** AI가 대사를 쓰는 블록인지. transition은 문구만, choice는 Spring이 직접 채운다. */
    public boolean hasMessages() {
        return this != TRANSITION && this != CHOICE;
    }
}
