package com.luvin.ai.dto.story;

/** 시즌 초기화 결과. 시즌이 없었으면 deletedSeasons·refunded*가 0이고, 정식 경로는 환불하지 않아 refunded*가 항상 0이다. */
public record AiSeasonResetView(
        /** 지운 시즌 수. */
        int deletedSeasons,
        /** 지운 시즌들에서 4~10화를 열며 쓴 소금빵 중 돌려준 양(개발 전용 경로만). */
        int refundedTokens,
        /** 초기화 뒤 소금빵 잔액. */
        int tokenBalance,
        /** 지운 시즌들에서 4~10화를 열며 쓴 식빵 중 돌려준 양(개발 전용 경로만). */
        int refundedBreadTokens,
        /** 초기화 뒤 식빵 잔액. */
        int breadTokenBalance
) {
}
