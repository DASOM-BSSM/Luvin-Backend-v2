package com.luvin.token.dto;

/**
 * @param tokenBalance        소금빵(무료, 미니게임 보상) 잔액
 * @param freeSimulationCount 남은 무료 시즌 수
 * @param breadTokenBalance   식빵(유료) 잔액
 */
public record TokenBalanceResponse(
        int tokenBalance,
        int freeSimulationCount,
        int breadTokenBalance
) {
}
