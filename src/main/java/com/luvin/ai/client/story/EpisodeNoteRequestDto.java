package com.luvin.ai.client.story;

/** 회차 완료 쪽지. 응답은 기존 쪽지와 같은 형식(LetterResponseDto)이다. 10화 쪽지가 시즌 종료 쪽지다. */
public record EpisodeNoteRequestDto(StoryContextDto context, int order, String episodeSummary, boolean seasonFinale) {
}
