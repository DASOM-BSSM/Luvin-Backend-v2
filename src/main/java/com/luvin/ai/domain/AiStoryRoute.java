package com.luvin.ai.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** 6화 선택(③) 결과로 정해지는 7~10화 흐름. 6화 선택 전에는 시즌의 route가 null이다. */
public enum AiStoryRoute {
    ROMANCE,
    SOLO;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
