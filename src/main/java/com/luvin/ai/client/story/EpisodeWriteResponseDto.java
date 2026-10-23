package com.luvin.ai.client.story;

import java.util.List;

public record EpisodeWriteResponseDto(Intro intro, List<StoryWrittenBlockDto> blocks) {

    /** 회차 상황 소개. */
    public record Intro(String title, String situation) {
    }
}
