package com.luvin.ai.client.story;

import java.util.List;

/** 10화 인터뷰룸. 시즌 선택 기록(거절 사유 포함)과 관계 기록은 context에 담긴다. */
public record InterviewRequestDto(StoryContextDto context, String settingScene, List<StoryBlockPlanDto> blocks) {
}
