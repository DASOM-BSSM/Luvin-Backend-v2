package com.luvin.ai.dto.story;

import java.util.List;

/**
 * GET /api/ai/seasons/episodes/{order}/content 응답.
 * status가 generating이면 intro/blocks가 null이다 — 앱은 잠시 뒤 같은 요청을 다시 보낸다.
 * failed면 errorCode가 있고, 다시 요청하면 생성을 다시 시작한다.
 */
public record AiEpisodeContentView(
        int order,
        /** generating | ready | failed */
        String status,
        String errorCode,
        AiStoryIntroView intro,
        AiStageIntroView stageIntro,
        List<AiSceneBlockView> blocks,
        boolean completed
) {
}
