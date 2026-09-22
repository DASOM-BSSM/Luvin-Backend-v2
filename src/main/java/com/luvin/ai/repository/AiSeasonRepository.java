package com.luvin.ai.repository;

import com.luvin.ai.domain.AiSeason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface AiSeasonRepository extends JpaRepository<AiSeason, Long> {
    Optional<AiSeason> findByMemberId(Long memberId);

    Optional<AiSeason> findBySeasonId(UUID seasonId);

    /** memberId가 소유한 season만 조회한다. 다른 사용자의 season 접근을 원천 차단한다 (요구사항 7절). */
    Optional<AiSeason> findByMemberIdAndSeasonId(Long memberId, UUID seasonId);

    /**
     * job polling worker가 갱신하는 동안 같은 season row를 동시에 여러 워커가 건드리지 않도록
     * 쓰기 락을 건다 (요구사항 4절의 "polling은 영속 상태를 기준으로 복구" 요건 지원).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiSeason> findWithLockBySeasonId(UUID seasonId);
}
