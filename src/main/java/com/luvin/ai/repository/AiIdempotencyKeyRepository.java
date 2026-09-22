package com.luvin.ai.repository;

import com.luvin.ai.domain.AiIdempotencyActionType;
import com.luvin.ai.domain.AiIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiIdempotencyKeyRepository extends JpaRepository<AiIdempotencyKey, Long> {
    Optional<AiIdempotencyKey> findByMemberIdAndActionTypeAndBusinessKey(
            Long memberId, AiIdempotencyActionType actionType, String businessKey);
}
