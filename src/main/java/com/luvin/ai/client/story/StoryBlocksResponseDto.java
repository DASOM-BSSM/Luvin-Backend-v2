package com.luvin.ai.client.story;

import java.util.List;

/** 선택 결과·인터뷰처럼 블록 대사만 돌려받는 호출의 응답. */
public record StoryBlocksResponseDto(List<StoryWrittenBlockDto> blocks) {
}
