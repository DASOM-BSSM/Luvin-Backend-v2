package com.luvin.ai.repository;

import com.luvin.ai.domain.AiSeason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiSeasonRepository extends JpaRepository<AiSeason, Long> {
    /**
     * 그 사용자의 "현재 시즌"(seasonNumber가 가장 큰 행). memberId당 여러 시즌 행이 있을 수 있으므로
     * 과거에 쓰던 findByMemberId(단일 Optional)는 더 이상 쓰지 않는다 — 2개 이상이면 예외가 난다.
     */
    Optional<AiSeason> findFirstByMemberIdOrderBySeasonNumberDesc(Long memberId);

    List<AiSeason> findAllByMemberIdOrderBySeasonNumberAsc(Long memberId);

    /** GET /api/ai/seasons 목록 응답용. 최신 시즌부터 전부 반환한다. */
    List<AiSeason> findAllByMemberIdOrderBySeasonNumberDesc(Long memberId);

    Optional<AiSeason> findByMemberIdAndSeasonNumber(Long memberId, Integer seasonNumber);

    Optional<AiSeason> findBySeasonId(UUID seasonId);

    /** memberId가 소유한 season만 조회한다. 다른 사용자의 season 접근을 원천 차단한다 (요구사항 7절). */
    Optional<AiSeason> findByMemberIdAndSeasonId(Long memberId, UUID seasonId);

    /**
     * job polling worker가 갱신하는 동안 같은 season row를 동시에 여러 워커가 건드리지 않도록
     * 쓰기 락을 건다 (요구사항 4절의 "polling은 영속 상태를 기준으로 복구" 요건 지원).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiSeason> findWithLockBySeasonId(UUID seasonId);

    /** 장면 블록 회차를 시작할 때(출연진 배정) 동시에 두 요청이 같은 시즌을 시작하지 않도록 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiSeason> findWithLockById(Long id);
}
