package com.luvin.ai.dto.story;

/** 회차 소개. episodeName은 회차 시작 화면 "Episode 01 :: {episodeName}"에 쓰는 한 줄(20자 이내). */
public record AiStoryIntroView(String title, String episodeName, String situation) {
}
