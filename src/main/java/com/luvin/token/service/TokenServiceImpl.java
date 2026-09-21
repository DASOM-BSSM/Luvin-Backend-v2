package com.luvin.token.service;

import com.luvin.common.exception.InsufficientTokenException;
import com.luvin.token.domain.TokenHistory;
import com.luvin.token.domain.TokenHistoryType;
import com.luvin.token.domain.TokenUsageType;
import com.luvin.token.domain.UserToken;
import com.luvin.token.dto.TokenBalanceResponse;
import com.luvin.token.dto.TokenHistoryListResponse;
import com.luvin.token.dto.TokenHistoryResponse;
import com.luvin.token.dto.TokenUseRequest;
import com.luvin.token.dto.TokenUseResponse;
import com.luvin.token.repository.TokenHistoryRepository;
import com.luvin.token.repository.UserTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TokenServiceImpl implements TokenService {

    private static final int DEFAULT_INITIAL_TOKEN_BALANCE = 0;
    private static final int DEFAULT_INITIAL_FREE_SIMULATION_COUNT = 0;

    private final UserTokenRepository userTokenRepository;
    private final TokenHistoryRepository tokenHistoryRepository;

    public TokenServiceImpl(UserTokenRepository userTokenRepository, TokenHistoryRepository tokenHistoryRepository) {
        this.userTokenRepository = userTokenRepository;
        this.tokenHistoryRepository = tokenHistoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TokenBalanceResponse getMyTokenStatus(Long memberId) {
        UserToken userToken = getOrCreateUserToken(memberId);
        return new TokenBalanceResponse(userToken.getTokenBalance(), userToken.getFreeSimulationCount());
    }

    @Override
    @Transactional
    public TokenUseResponse useToken(Long memberId, TokenUseRequest request) {
        UserToken userToken = getOrCreateUserToken(memberId);

        if (userToken.getTokenBalance() < request.amount()) {
            throw new InsufficientTokenException(request.amount(), userToken.getTokenBalance());
        }

        userToken.use(request.amount());

        tokenHistoryRepository.save(new TokenHistory(
                memberId,
                TokenHistoryType.USE,
                -request.amount(),
                resolveUseReason(request.usageType())
        ));

        return new TokenUseResponse(request.amount(), userToken.getTokenBalance(), "토큰이 사용되었습니다.");
    }

    @Override
    @Transactional(readOnly = true)
    public TokenHistoryListResponse getHistory(Long memberId) {
        List<TokenHistoryResponse> histories = tokenHistoryRepository
                .findAllByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(TokenHistoryResponse::from)
                .collect(Collectors.toList());
        return new TokenHistoryListResponse(histories);
    }

    @Override
    @Transactional
    public void grantToken(Long memberId, int amount, String reason) {
        if (amount <= 0) {
            return;
        }

        UserToken userToken = getOrCreateUserToken(memberId);
        userToken.earn(amount);

        tokenHistoryRepository.save(new TokenHistory(
                memberId,
                TokenHistoryType.REWARD,
                amount,
                reason
        ));
    }

    private UserToken getOrCreateUserToken(Long memberId) {
        return userTokenRepository.findByMemberId(memberId)
                .orElseGet(() -> userTokenRepository.save(
                        new UserToken(memberId, DEFAULT_INITIAL_TOKEN_BALANCE, DEFAULT_INITIAL_FREE_SIMULATION_COUNT)
                ));
    }

    private String resolveUseReason(TokenUsageType usageType) {
        return switch (usageType) {
            case SIMULATION -> "러빈지옥 시뮬레이션";
        };
    }
}