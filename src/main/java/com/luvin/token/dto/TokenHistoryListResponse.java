package com.luvin.token.dto;

import java.util.List;

public record TokenHistoryListResponse(
        List<TokenHistoryResponse> histories
) {
}