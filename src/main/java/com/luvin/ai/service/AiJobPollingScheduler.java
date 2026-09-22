package com.luvin.ai.service;

import com.luvin.ai.client.AiServiceClient;
import com.luvin.ai.client.dto.JobResponseDto;
import com.luvin.ai.client.exception.AiServiceException;
import com.luvin.ai.config.AiServiceProperties;
import com.luvin.ai.domain.AiEpisodeProgress;
import com.luvin.ai.domain.AiJobStatus;
import com.luvin.ai.domain.AiRerollRequest;
import com.luvin.ai.repository.AiEpisodeProgressRepository;
import com.luvin.ai.repository.AiRerollRequestRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.security.OwnerSubjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * generation/reroll job polling worker. Spring request thread는 202 응답을 받은 즉시 종료하고,
 * 이 워커가 영속 상태(job_status, next_poll_at)를 기준으로 완료 여부를 확인한다 (요구사항 5.3).
 *
 * Spring 재시작 후에도 QUEUED/RUNNING/RETRY_WAIT 상태로 남아있는 row를 그대로 다시 polling 대상으로
 * 잡기 때문에 별도 복구 로직 없이 재개된다 (요구사항 4절, 8절). 같은 job을 여러 인스턴스가 동시에
 * 처리해도 각 entity의 @Version(jpaVersion) 낙관적 락 덕분에 최종 저장은 안전하게 충돌 처리된다.
 */
@Component
public class AiJobPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiJobPollingScheduler.class);
    private static final List<AiJobStatus> PENDING_STATUSES =
            List.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING, AiJobStatus.RETRY_WAIT);

    private final AiServiceClient aiServiceClient;
    private final AiServiceProperties properties;
    private final OwnerSubjectProvider ownerSubjectProvider;
    private final AiEpisodeProgressRepository episodeProgressRepository;
    private final AiRerollRequestRepository rerollRequestRepository;
    private final AiSeasonRepository seasonRepository;
    private final TransactionTemplate transactionTemplate;

    public AiJobPollingScheduler(AiServiceClient aiServiceClient,
                                  AiServiceProperties properties,
                                  OwnerSubjectProvider ownerSubjectProvider,
                                  AiEpisodeProgressRepository episodeProgressRepository,
                                  AiRerollRequestRepository rerollRequestRepository,
                                  AiSeasonRepository seasonRepository,
                                  PlatformTransactionManager transactionManager) {
        this.aiServiceClient = aiServiceClient;
        this.properties = properties;
        this.ownerSubjectProvider = ownerSubjectProvider;
        this.episodeProgressRepository = episodeProgressRepository;
        this.rerollRequestRepository = rerollRequestRepository;
        this.seasonRepository = seasonRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "PT1S")
    public void pollGenerationJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<AiEpisodeProgress> due = episodeProgressRepository
                .findAllByJobStatusInAndNextPollAtLessThanEqual(PENDING_STATUSES, now);
        for (AiEpisodeProgress progress : due) {
            try {
                pollOneGenerationJob(progress.getId());
            } catch (Exception e) {
                log.warn("generation job polling failed, will retry on next tick: progressId={}", progress.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "PT1S")
    public void pollRerollJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<AiRerollRequest> due = rerollRequestRepository
                .findAllByJobStatusInAndNextPollAtLessThanEqual(PENDING_STATUSES, now);
        for (AiRerollRequest reroll : due) {
            try {
                pollOneRerollJob(reroll.getId());
            } catch (Exception e) {
                log.warn("reroll job polling failed, will retry on next tick: rerollId={}", reroll.getId(), e);
            }
        }
    }

    private void pollOneGenerationJob(Long progressId) {
        transactionTemplate.executeWithoutResult(status ->
                episodeProgressRepository.findById(progressId).ifPresent(progress -> {
                    if (progress.getJobId() == null || !progress.getJobStatus().isPending()) {
                        return; // 다른 워커가 먼저 처리했을 수 있다 (멱등).
                    }
                    Long memberId = progress.getSeason().getMemberId();
                    String ownerSubject = ownerSubjectProvider.resolve(memberId);

                    JobResponseDto job = fetchJobSafely(ownerSubject, progress.getJobId());
                    if (job == null) {
                        scheduleRetry(progress);
                        return;
                    }

                    AiJobStatus jobStatus = AiJobStatus.fromWire(job.status(), null);
                    if (jobStatus == AiJobStatus.SUCCEEDED) {
                        progress.markSucceeded(job.resultVersionId());
                        // generation 완료 후 최신 season(current_episode 등)을 동기화한다.
                        try {
                            var latest = aiServiceClient.getSeason(ownerSubject, progress.getSeason().getSeasonId());
                            seasonRepository.findById(progress.getSeason().getId())
                                    .ifPresent(s -> s.syncFromAi(latest.revision(), latest.currentEpisode()));
                        } catch (AiServiceException syncFailure) {
                            log.warn("season sync after generation success failed, will be corrected on next GET season call");
                        }
                    } else if (jobStatus == AiJobStatus.FAILED) {
                        progress.markFailed(job.errorCode());
                    } else {
                        scheduleRetry(progress);
                    }
                }));
    }

    private void pollOneRerollJob(Long rerollId) {
        transactionTemplate.executeWithoutResult(status ->
                rerollRequestRepository.findById(rerollId).ifPresent(reroll -> {
                    if (reroll.getJobId() == null || !reroll.getJobStatus().isPending()) {
                        return;
                    }
                    Long memberId = reroll.getSeason().getMemberId();
                    String ownerSubject = ownerSubjectProvider.resolve(memberId);

                    JobResponseDto job = fetchJobSafely(ownerSubject, reroll.getJobId());
                    if (job == null) {
                        scheduleRetry(reroll);
                        return;
                    }

                    AiJobStatus jobStatus = AiJobStatus.fromWire(job.status(), null);
                    if (jobStatus == AiJobStatus.SUCCEEDED) {
                        reroll.markSucceeded(job.resultVersionId());
                        // 성공 후에는 새 version의 첫 페이지부터 다시 조회해야 한다 (요구사항 5.5) —
                        // 해당 화의 진행 상태 version을 교체하고 lastSeenSequence를 초기화한다.
                        episodeProgressRepository.findBySeason_IdAndEpisodeNumber(
                                        reroll.getSeason().getId(), reroll.getEpisodeNumber())
                                .ifPresent(progress -> progress.replaceVersion(job.resultVersionId()));
                    } else if (jobStatus == AiJobStatus.FAILED) {
                        reroll.markFailed(job.errorCode());
                    } else {
                        scheduleRetry(reroll);
                    }
                }));
    }

    private JobResponseDto fetchJobSafely(String ownerSubject, java.util.UUID jobId) {
        try {
            return aiServiceClient.getJob(ownerSubject, jobId);
        } catch (AiServiceException e) {
            // 네트워크/서버 오류는 job 실패가 아니다 (요구사항 5.3). null을 반환해 다음 tick에 재시도한다.
            log.warn("job polling call failed transiently: jobId={}, error={}", jobId, e.getClass().getSimpleName());
            return null;
        }
    }

    private void scheduleRetry(AiEpisodeProgress progress) {
        int attempts = progress.getPollAttempts() + 1;
        Duration backoff = computeBackoff(attempts);
        progress.markPending(AiJobStatus.RUNNING, LocalDateTime.now().plus(backoff), attempts);
    }

    private void scheduleRetry(AiRerollRequest reroll) {
        int attempts = reroll.getPollAttempts() + 1;
        Duration backoff = computeBackoff(attempts);
        reroll.markPending(AiJobStatus.RUNNING, LocalDateTime.now().plus(backoff), attempts);
    }

    /** 1초부터 시작해 최대 5초까지 지수 backoff (요구사항 5.3). */
    private Duration computeBackoff(int attempts) {
        long seconds = Math.min(
                properties.polling().initialInterval().toSeconds() * (1L << Math.min(attempts, 10)),
                properties.polling().maxInterval().toSeconds());
        return Duration.ofSeconds(Math.max(seconds, properties.polling().initialInterval().toSeconds()));
    }
}
