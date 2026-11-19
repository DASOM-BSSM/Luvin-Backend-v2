package com.luvin.push.service;

import com.luvin.push.domain.PushPlatform;
import com.luvin.push.domain.PushToken;
import com.luvin.push.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 기기 토큰 등록. 같은 토큰이 있으면 덮어쓴다(다른 계정이 등록한 토큰이면 이 계정으로 옮긴다). 같은 토큰을 동시에
     * 처음 등록하면 진 쪽이 unique 위반으로 롤백되므로 한 번 더 시도해 이긴 쪽 row를 덮어쓴다.
     */
    public void register(Long memberId, String token, String platform) {
        PushPlatform parsed = PushPlatform.fromWire(platform);
        try {
            transactionTemplate.executeWithoutResult(status -> upsert(memberId, token, parsed));
        } catch (DataIntegrityViolationException concurrentInsert) {
            transactionTemplate.executeWithoutResult(status -> upsert(memberId, token, parsed));
        }
    }

    private void upsert(Long memberId, String token, PushPlatform platform) {
        pushTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> existing.reassign(memberId, platform),
                () -> pushTokenRepository.saveAndFlush(new PushToken(memberId, token, platform)));
    }

    /** 로그아웃: 내 토큰만 지운다. 없으면 아무것도 하지 않는다. */
    @Transactional
    public void unregister(Long memberId, String token) {
        pushTokenRepository.deleteByMemberIdAndToken(memberId, token);
    }

    @Transactional(readOnly = true)
    public List<String> tokensOf(Long memberId) {
        return pushTokenRepository.findAllByMemberId(memberId).stream().map(PushToken::getToken).toList();
    }

    /** Expo가 DeviceNotRegistered를 돌려준 토큰. */
    @Transactional
    public void removeTokens(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            pushTokenRepository.deleteByTokenIn(tokens);
        }
    }
}
