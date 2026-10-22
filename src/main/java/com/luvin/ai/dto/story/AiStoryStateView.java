package com.luvin.ai.dto.story;

import java.util.List;
import java.util.UUID;

/** 장면 블록 회차 시즌 상태. 새 흐름을 시작하기 전(1화 콘텐츠 요청 전)이면 시즌 상태 응답의 story가 null이다. */
public record AiStoryStateView(
        /** school | campus | office | cafe */
        String setting,
        Integer currentOrder,
        /** romance | solo | null(6화 선택 전) */
        String route,
        UUID romancePartnerId,
        List<ChoiceHistory> choices,
        List<BreadStatus> breads
) {

    public record ChoiceHistory(int pointId, int episodeOrder, UUID breadId, boolean accepted) {
    }

    public record BreadStatus(UUID breadId, String name, String role, String statusText) {
    }
}
