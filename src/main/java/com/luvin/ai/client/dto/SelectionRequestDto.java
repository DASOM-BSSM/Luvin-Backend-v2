package com.luvin.ai.client.dto;

import java.util.UUID;

/**
 * source=user(1화 선택) 또는 source=minigame(3화 미니게임 결과)만 사용한다 (요구사항 5.4).
 * 실패한 미니게임 결과는 partnerId를 null로 보낸다.
 */
public record SelectionRequestDto(
        Integer expectedRevision,
        UUID sourceEventId,
        String source,
        UUID partnerId,
        String gameResult
) {
    public static final String SOURCE_USER = "user";
    public static final String SOURCE_MINIGAME = "minigame";
    public static final String GAME_RESULT_SUCCESS = "success";
    public static final String GAME_RESULT_FAILURE = "failure";
}
