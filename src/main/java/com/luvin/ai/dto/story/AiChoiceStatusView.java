package com.luvin.ai.dto.story;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 선택 결과 장면 생성 상태(POST choices는 접수 후 바로 응답하고 결과 장면은 백그라운드에서 만든다).
 * generating이면 시즌 상태(GET /me의 story.choice)를 폴링하고, ready가 되면 같은 선택을 다시 보내거나 회차 콘텐츠를 다시
 * 받아 결과 장면을 본다. failed면 같은 선택을 다시 보내 재시도한다.
 */
public record AiChoiceStatusView(
        int order,
        @Schema(minimum = "1", maximum = "4") int pointId,
        /** null이면 혼자 있기. */
        UUID breadId,
        /** generating | ready | failed */
        @Schema(allowableValues = {"generating", "ready", "failed"}) String status,
        /** failed일 때만: STORY_GENERATION_FAILED */
        @Schema(types = {"string", "null"}) String errorCode
) {
    public static final String GENERATING = "generating";
    public static final String READY = "ready";
    public static final String FAILED = "failed";
}
