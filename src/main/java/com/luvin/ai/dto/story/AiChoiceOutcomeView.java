package com.luvin.ai.dto.story;

import java.util.List;

/**
 * POST choices 응답. choice.status가 ready면 blocks에 선택 결과 장면(choice_result, 거절이면 대체 장면 포함)이 있고,
 * generating·failed면 blocks는 빈 배열이다. season은 갱신된 시즌 상태(선택 기록과 흐름은 접수 때 바로 반영된다).
 */
public record AiChoiceOutcomeView(List<AiSceneBlockView> blocks, AiStoryStateView season, AiChoiceStatusView choice) {
}
