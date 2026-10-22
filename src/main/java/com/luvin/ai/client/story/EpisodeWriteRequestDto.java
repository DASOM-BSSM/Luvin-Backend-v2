package com.luvin.ai.client.story;

import java.util.List;

/** 회차 집필. referenceEpisode는 참고 줄거리로 쓸 기존 본편 회차 번호(1·2·3·6·10화만, 그 외 null). */
public record EpisodeWriteRequestDto(
        StoryContextDto context,
        int order,
        String commonEvent,
        String settingScene,
        String route,
        Integer referenceEpisode,
        List<String> openThreads,
        List<StoryBlockPlanDto> blocks
) {
}
