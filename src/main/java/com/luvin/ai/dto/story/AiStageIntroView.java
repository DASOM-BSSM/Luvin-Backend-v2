package com.luvin.ai.dto.story;

import java.util.List;
import java.util.UUID;

/** 1화 무대 소개. 1화 외에는 null로 내려간다. */
public record AiStageIntroView(String setting, String title, String subtitle, List<StageCast> cast) {

    /** role은 배경별 역할 소개(예: 학교 "같은 반 반장"). description은 내 빵이면 null. */
    public record StageCast(UUID breadId, String name, String breadType, String role, String description) {
    }
}
