package com.luvin.token.dto;

public record TokenBalanceResponse(
        int tokenBalance,
        int freeSimulationCount
) {
}