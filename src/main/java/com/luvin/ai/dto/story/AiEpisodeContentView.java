package com.luvin.ai.dto.story;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * GET /api/ai/seasons/episodes/{order}/content 응답.
 * status가 generating이면 intro/blocks가 null이다 — 앱은 잠시 뒤 같은 요청을 다시 보낸다.
 * failed면 errorCode가 있다. 실패 뒤 첫 요청만 failed이고, 그다음 요청은 생성을 다시 시작한다.
 * generating이면 progress에 생성 단계와 다시 쓰는 중인지가 있다.
 */
public record AiEpisodeContentView(
        int order,
        /** generating | ready | failed */
        @Schema(allowableValues = {"generating", "ready", "failed"}) String status,
        String errorCode,
        AiStoryIntroView intro,
        AiStageIntroView stageIntro,
        List<AiSceneBlockView> blocks,
        boolean completed,
        /** 이 회차 1:1 장면(pair 블록)에 남긴 피드백. 선택 결과 뒤 대체 장면에 남긴 것도 포함한다. */
        List<AiStoryFeedbackView> feedbacks,
        /** 이 회차 1:1 장면에서 좋아요한 메시지 id. */
        List<java.util.UUID> likedMessageIds,
        /** generating일 때만: 생성 단계와 다시 쓰는 중인지. 그 외 null */
        @Schema(types = {"object", "null"}) AiGenerationProgressView progress
) {
}
