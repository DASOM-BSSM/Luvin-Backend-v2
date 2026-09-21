package com.luvin.token.dto;

import com.luvin.token.domain.TokenHistory;
import com.luvin.token.domain.TokenHistoryType;

import java.time.LocalDateTime;

public record TokenHistoryResponse(
        Long historyId,
        TokenHistoryType type,
        int amount,
        String reason,
        LocalDateTime createdAt
) {
    public static TokenHistoryResponse from(TokenHistory history) {
        return new TokenHistoryResponse(
                history.getHistoryId(),
                history.getType(),
                history.getAmount(),
                history.getReason(),
                history.getCreatedAt()
        );
    }
}