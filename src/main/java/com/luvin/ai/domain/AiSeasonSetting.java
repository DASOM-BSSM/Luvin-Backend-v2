package com.luvin.ai.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 시즌 배경. 시즌을 시작할 때 한 번 정하고 시즌 동안 바꾸지 않는다 — 첫 시즌은 사용자 나이로, 그 뒤로는 나이에 맞는
 * 배경 중 지난 시즌들과 다르게 고른다({@code AiSeasonSettingPolicy}).
 * 1화 무대 소개의 제목/부제목은 배경마다 고정 문구다 — 제목은 "In School" 표기, 부제목은 30자 이내(계약 0-1장).
 */
public enum AiSeasonSetting {
    SCHOOL("In School", "같은 반 친구들 사이에서 구워지는 내 반죽의 이야기"),
    CAMPUS("In Campus", "같은 과 동기들 사이에서 부푸는 내 반죽의 이야기"),
    CAFE("In Cafe", "같은 매장 근무 팀 안에서 구워지는 내 반죽의 이야기"),
    OFFICE("In Office", "같은 팀 동기들 사이에서 익어가는 내 반죽의 이야기");

    private final String title;
    private final String subtitle;

    AiSeasonSetting(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
