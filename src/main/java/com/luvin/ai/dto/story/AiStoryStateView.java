package com.luvin.ai.dto.story;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** 장면 블록 회차 시즌 상태. 새 흐름을 시작하기 전(1화 콘텐츠 요청 전)이면 시즌 상태 응답의 story가 null이다. */
public record AiStoryStateView(
        /** school | campus | office | cafe */
        @Schema(allowableValues = {"school", "campus", "office", "cafe"}) String setting,
        Integer currentOrder,
        /** 완료한 회차(오름차순). 10화까지 완료하면 currentOrder는 10 그대로이고 시즌 status가 COMPLETED다. */
        List<Integer> completedOrders,
        /** romance | solo | null(6화 선택 전) */
        @Schema(types = {"string", "null"}, allowableValues = {"romance", "solo"}) String route,
        UUID romancePartnerId,
        List<ChoiceHistory> choices,
        List<BreadStatus> breads,
        /** 지금 볼 수 있는 회차(오름차순). 1~3화는 항상 들어 있고, 4~10화는 연 회차만. */
        List<Integer> unlockedOrders,
        /** 4~10화를 처음 열 때 쓰는 토큰 수. */
        int episodeTokenCost,
        /** 진행 중이거나 실패한 회차 완료(POST complete). 없으면 null. */
        @Schema(types = {"object", "null"}) Completion completion
) {

    /**
     * 회차 완료 진행 상태. completing이면 시즌 상태를 폴링하고, failed면 POST complete를 다시 보내 재시도한다.
     * 완료되면 completion이 null이 되고 completedOrders에 들어간다.
     */
    public record Completion(
            int order,
            /** completing | failed */
            @Schema(allowableValues = {"completing", "failed"}) String status,
            /** failed일 때만: STORY_GENERATION_FAILED */
            @Schema(types = {"string", "null"}) String errorCode
    ) {
        public static final String COMPLETING = "completing";
        public static final String FAILED = "failed";
    }

    public record ChoiceHistory(@Schema(minimum = "1", maximum = "4") int pointId, int episodeOrder, UUID breadId, boolean accepted) {
    }

    public record BreadStatus(UUID breadId, String name, String role, String statusText) {
    }
}
