package com.luvin.ai.repository;

import com.luvin.ai.domain.AiEpisodeProgress;
import com.luvin.ai.domain.AiJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiEpisodeProgressRepository extends JpaRepository<AiEpisodeProgress, Long> {
    Optional<AiEpisodeProgress> findBySeason_IdAndEpisodeNumber(Long seasonPk, Integer episodeNumber);

    /** job SSE 구독 전 소유권 확인용. 새 generation을 요청하면 jobId가 덮어써지므로 최신 job만 맞는다. */
    boolean existsByJobIdAndSeason_MemberId(UUID jobId, Long memberId);

    /** Spring 재시작 후 미완료 job polling 복구에 사용한다 (요구사항 4절, 8절). */
    List<AiEpisodeProgress> findAllByJobStatusIn(List<AiJobStatus> statuses);

    /** backoff 시각이 지난 pending job만 polling 대상으로 뽑는다 (요구사항 5.3). */
    List<AiEpisodeProgress> findAllByJobStatusInAndNextPollAtLessThanEqual(
            List<AiJobStatus> statuses, LocalDateTime now);
}
