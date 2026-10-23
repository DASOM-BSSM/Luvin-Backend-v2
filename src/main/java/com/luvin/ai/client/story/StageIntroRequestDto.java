package com.luvin.ai.client.story;

/** 1화 생성 전: 배경에 맞는 출연진 역할·소개를 받는다(예: 학교 "같은 반 반장"). */
public record StageIntroRequestDto(StoryContextDto context, String settingTitle, String settingSubtitle) {
}
