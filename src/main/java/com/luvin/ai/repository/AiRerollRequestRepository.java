package com.luvin.ai.repository;

import com.luvin.ai.domain.AiJobStatus;
import com.luvin.ai.domain.AiRerollRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiRerollRequestRepository extends JpaRepository<AiRerollRequest, Long> {
    Optional<AiRerollRequest> findByIdempotencyKey(UUID idempotencyKey);

    Optional<AiRerollRequest> findFirstBySeason_IdAndEpisodeNumberOrderByCreatedAtDesc(
            Long seasonId, Integer episodeNumber);

    /** job SSE 구독 전 소유권 확인용. */
    boolean existsByJobIdAndSeason_MemberId(UUID jobId, Long memberId);

    /** job SSE가 completed/error를 받았을 때 그 job을 바로 반영하기 위해 쓴다. */
    Optional<AiRerollRequest> findByJobId(UUID jobId);

    List<AiRerollRequest> findAllByJobStatusIn(List<AiJobStatus> statuses);

    List<AiRerollRequest> findAllByJobStatusInAndNextPollAtLessThanEqual(
            List<AiJobStatus> statuses, LocalDateTime now);
}
