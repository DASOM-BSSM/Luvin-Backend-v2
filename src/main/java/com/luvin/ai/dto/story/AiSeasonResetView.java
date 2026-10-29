package com.luvin.ai.dto.story;

/** 개발 전용 시즌 초기화 결과. 시즌이 없었으면 deletedSeasons·refundedTokens가 0이다. */
public record AiSeasonResetView(
        /** 지운 시즌 수. */
        int deletedSeasons,
        /** 지운 시즌들에서 4~10화를 열며 쓴 토큰 중 돌려준 양. */
        int refundedTokens,
        /** 환불 뒤 토큰 잔액. */
        int tokenBalance
) {
}
