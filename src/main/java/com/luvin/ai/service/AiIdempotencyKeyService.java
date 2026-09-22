package com.luvin.ai.service;

import com.luvin.ai.domain.AiIdempotencyActionType;
import com.luvin.ai.domain.AiIdempotencyKey;
import com.luvin.ai.repository.AiIdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * 한 사용자 동작을 재시도할 때는 같은 idempotency key를 재사용하고, 새로운 동작에만 새 key를 발급한다
 * (요구사항 3.2). businessKey가 동작의 동일성을 결정한다.
 *
 * REQUIRES_NEW 트랜잭션에서 유니크 제약 위반을 즉시 catch해야 하므로,
 * (같은 클래스 self-invocation으로 인해 프록시가 무시되는 문제를 피하기 위해) TransactionTemplate을 직접 사용한다.
 */
@Service
public class AiIdempotencyKeyService {

    private final AiIdempotencyKeyRepository repository;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public AiIdempotencyKeyService(AiIdempotencyKeyRepository repository,
                                    org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    /**
     * businessKey에 대한 idempotency key를 조회하고, 없으면 새로 발급해서 저장한다.
     * 동시에 같은 businessKey로 두 요청이 들어와도 유니크 제약으로 하나만 생성되도록 재조회한다.
     */
    @Transactional(readOnly = true)
    public UUID resolveOrIssue(Long memberId, AiIdempotencyActionType actionType, String businessKey) {
        return repository.findByMemberIdAndActionTypeAndBusinessKey(memberId, actionType, businessKey)
                .map(AiIdempotencyKey::getIdempotencyKey)
                .orElseGet(() -> issueNew(memberId, actionType, businessKey));
    }

    private UUID issueNew(Long memberId, AiIdempotencyActionType actionType, String businessKey) {
        try {
            return requiresNewTransactionTemplate.execute(status -> {
                AiIdempotencyKey saved = repository.save(
                        new AiIdempotencyKey(memberId, actionType, businessKey, UUID.randomUUID()));
                repository.flush();
                return saved.getIdempotencyKey();
            });
        } catch (DataIntegrityViolationException raceLoser) {
            // 동시 요청 경쟁에서 진 쪽: 먼저 저장된 key를 재조회해서 재사용한다.
            return repository.findByMemberIdAndActionTypeAndBusinessKey(memberId, actionType, businessKey)
                    .map(AiIdempotencyKey::getIdempotencyKey)
                    .orElseThrow(() -> raceLoser);
        }
    }
}
