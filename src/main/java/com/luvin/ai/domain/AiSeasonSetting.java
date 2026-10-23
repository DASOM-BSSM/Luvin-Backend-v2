package com.luvin.ai.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 시즌 배경. 시즌을 시작할 때 사용자 나이로 한 번 정하고 시즌 동안 바꾸지 않는다({@code AiSeasonSettingPolicy}).
 * 1화 무대 소개의 제목/부제목은 배경마다 고정 문구다.
 */
public enum AiSeasonSetting {
    SCHOOL("IN SCHOOL", "같은 반 친구들 사이에서 구워지는 내 반죽의 이야기"),
    CAMPUS("IN CAMPUS", "같은 과 동기들 사이에서 부풀어 오르는 내 반죽의 이야기"),
    CAFE("IN CAFE", "같은 매장 근무 팀 안에서 구워지는 내 반죽의 이야기"),
    OFFICE("IN OFFICE", "같은 팀 동기들 사이에서 익어가는 내 반죽의 이야기");

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
