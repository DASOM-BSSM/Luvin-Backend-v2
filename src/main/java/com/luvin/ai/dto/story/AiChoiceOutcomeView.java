package com.luvin.ai.dto.story;

import java.util.List;

/** POST choices 응답: 선택 결과 장면(choice_result, 거절이면 대체 장면 포함)과 갱신된 시즌 상태. */
public record AiChoiceOutcomeView(List<AiSceneBlockView> blocks, AiStoryStateView season) {
}
