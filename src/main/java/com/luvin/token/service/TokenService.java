package com.luvin.token.service;

import com.luvin.token.dto.TokenBalanceResponse;
import com.luvin.token.dto.TokenHistoryListResponse;
import com.luvin.token.dto.TokenUseRequest;
import com.luvin.token.dto.TokenUseResponse;

public interface TokenService {
    TokenBalanceResponse getMyTokenStatus(Long memberId);
    TokenUseResponse useToken(Long memberId, TokenUseRequest request);
    TokenHistoryListResponse getHistory(Long memberId);
    void grantToken(Long memberId, int amount, String reason);
}