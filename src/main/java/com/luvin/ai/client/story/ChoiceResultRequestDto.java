package com.luvin.ai.client.story;

import java.util.List;
import java.util.UUID;

/** 선택 결과 장면(+ 거절이면 대체 장면). 판정은 Spring이 끝낸 상태로 보낸다. */
public record ChoiceResultRequestDto(
        StoryContextDto context,
        int order,
        int pointId,
        /** null이면 혼자 있기 */
        UUID breadId,
        boolean accepted,
        String reasonCode,
        boolean shortReply,
        String settingScene,
        List<StoryBlockPlanDto> blocks
) {
}
