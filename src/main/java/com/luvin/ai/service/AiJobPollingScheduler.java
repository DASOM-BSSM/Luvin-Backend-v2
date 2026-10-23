package com.luvin.ai.service;

import com.luvin.ai.client.AiServiceClient;
import com.luvin.ai.client.dto.JobResponseDto;
import com.luvin.ai.client.dto.LetterResponseDto;
import com.luvin.ai.client.exception.AiServiceException;
import com.luvin.ai.config.AiServiceProperties;
import com.luvin.ai.domain.AiEpisodeProgress;
import com.luvin.ai.domain.AiJobStatus;
import com.luvin.ai.domain.PartnerLetter;
import com.luvin.ai.repository.AiEpisodeProgressRepository;
import com.luvin.ai.repository.AiSeasonRepository;
import com.luvin.ai.repository.PartnerLetterRepository;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * generation/쪽지 job polling worker. Spring request thread는 202 응답을 받은 즉시 종료하고,
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
    /** FAILED도 함께 뽑는다 — 쪽지 job은(회차 생성과 달리) 로컬 한도 내에서 자동 재시도한다. */
    private static final List<AiJobStatus> LETTER_DUE_STATUSES =
            List.of(AiJobStatus.QUEUED, AiJobStatus.RUNNING, AiJobStatus.RETRY_WAIT, AiJobStatus.FAILED);
    private static final int FINAL_EPISODE = 5;
    private static final long LETTER_RETRY_COOLDOWN_SECONDS = 30;

    private final AiServiceClient aiServiceClient;
    private final AiServiceProperties properties;
    private final OwnerSubjectProvider ownerSubjectProvider;
    private final AiEpisodeProgressRepository episodeProgressRepository;
    private final AiSeasonRepository seasonRepository;
    private final PartnerLetterRepository partnerLetterRepository;
    private final AiSeasonOrchestrationService orchestrationService;
    private final TransactionTemplate transactionTemplate;

    public AiJobPollingScheduler(AiServiceClient aiServiceClient,
                                  AiServiceProperties properties,
                                  OwnerSubjectProvider ownerSubjectProvider,
                                  AiEpisodeProgressRepository episodeProgressRepository,
                                  AiSeasonRepository seasonRepository,
                                  PartnerLetterRepository partnerLetterRepository,
                                  AiSeasonOrchestrationService orchestrationService,
                                  PlatformTransactionManager transactionManager) {
        this.aiServiceClient = aiServiceClient;
        this.properties = properties;
        this.ownerSubjectProvider = ownerSubjectProvider;
        this.episodeProgressRepository = episodeProgressRepository;
        this.seasonRepository = seasonRepository;
        this.partnerLetterRepository = partnerLetterRepository;
        this.orchestrationService = orchestrationService;
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
    public void pollLetterJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<PartnerLetter> due = partnerLetterRepository
                .findAllByJobStatusInAndNextPollAtLessThanEqual(LETTER_DUE_STATUSES, now);
        for (PartnerLetter letter : due) {
            try {
                pollOneLetterJob(letter.getId());
            } catch (Exception e) {
                log.warn("letter job polling failed, will retry on next tick: letterId={}", letter.getId(), e);
            }
        }
    }

    /**
     * job SSE에서 completed/error를 받았을 때, 다음 polling 차례(최대 5초 backoff)를 기다리지 않고 그 job을 바로
     * 반영한다. 앱이 completed를 받자마자 조회 API를 불러도 409(아직 완료되지 않음)가 나지 않게 하기 위함이다.
     * 평소 polling과 같은 메서드를 타므로 이미 반영된 job(pending 아님)이면 아무것도 하지 않는다.
     * 쪽지 job은 앱이 jobId를 받지 않아 SSE로 구독할 일이 없으므로 다루지 않는다.
     */
    public void pollJobNow(UUID jobId) {
        episodeProgressRepository.findByJobId(jobId).ifPresent(progress -> pollOneGenerationJob(progress.getId()));
    }

    private void pollOneGenerationJob(Long progressId) {
        AtomicLong finalEpisodeSeasonPk = new AtomicLong(-1);
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
                        if (progress.getEpisodeNumber() == FINAL_EPISODE) {
                            finalEpisodeSeasonPk.set(progress.getSeason().getId());
                        }
                    } else if (jobStatus == AiJobStatus.FAILED) {
                        progress.markFailed(job.errorCode());
                    } else {
                        scheduleRetry(progress);
                    }
                }));

        // EP5 generation이 막 성공했을 때만: 클라이언트가 report를 조회할 때까지 기다리지 않고 여기서
        // 바로 "SECRET LOVE" 쪽지 생성을 시작한다 — 도착 안내가 뜨기 전에 미리 시작해야 쪽지를 펼치는
        // 로딩 상태가 짧게 보인다. 실패해도 EP5 성공 처리 자체에는 영향이 없도록 best-effort로 다룬다.
        if (finalEpisodeSeasonPk.get() > 0) {
            try {
                orchestrationService.ensureLetterGenerationStarted(finalEpisodeSeasonPk.get());
            } catch (Exception e) {
                log.warn("letter generation trigger failed after EP5 success, will retry via scheduled sweep: seasonPk={}",
                        finalEpisodeSeasonPk.get(), e);
            }
        }
    }

    private void pollOneLetterJob(Long letterId) {
        boolean[] shouldRetryGeneration = {false};
        long[] retrySeasonPk = {-1};

        transactionTemplate.executeWithoutResult(status ->
                partnerLetterRepository.findById(letterId).ifPresent(letter -> {
                    if (letter.getJobStatus() == AiJobStatus.FAILED) {
                        if (letter.hasReachedMaxAttempts()) {
                            return; // 로컬 한도 도달 — 더 이상 자동 재시도하지 않는다.
                        }
                        // 재시도 접수(ensureLetterGenerationStarted)가 지연/거절되더라도 다음 tick에
                        // 바로 또 집히지 않도록 cooldown을 먼저 당겨 둔다. 실제 접수에 성공하면
                        // markJobAccepted가 nextPollAt을 "지금"으로 다시 되돌린다.
                        letter.postponeRetry(LocalDateTime.now().plusSeconds(LETTER_RETRY_COOLDOWN_SECONDS));
                        shouldRetryGeneration[0] = true;
                        retrySeasonPk[0] = letter.getSeason().getId();
                        return;
                    }
                    if (letter.getJobId() == null || !letter.getJobStatus().isPending()) {
                        return; // 다른 워커가 먼저 처리했을 수 있다 (멱등).
                    }
                    Long memberId = letter.getMemberId();
                    String ownerSubject = ownerSubjectProvider.resolve(memberId);

                    JobResponseDto job = fetchJobSafely(ownerSubject, letter.getJobId());
                    if (job == null) {
                        scheduleRetry(letter);
                        return;
                    }

                    AiJobStatus jobStatus = AiJobStatus.fromWire(job.status(), null);
                    if (jobStatus == AiJobStatus.SUCCEEDED) {
                        try {
                            LetterResponseDto letterResponse =
                                    aiServiceClient.getLetter(ownerSubject, letter.getSeason().getSeasonId());
                            letter.markSucceeded(letterResponse.finalPartnerId(), letterResponse.body(),
                                    letterResponse.renderMode());
                        } catch (AiServiceException fetchFailure) {
                            // job은 끝났는데 본문을 못 가져온 경우 — job 상태는 그대로 두고 짧게 재시도한다.
                            log.warn("letter fetch after job success failed, will retry: letterId={}", letter.getId());
                            scheduleRetry(letter);
                        }
                    } else if (jobStatus == AiJobStatus.FAILED) {
                        letter.markFailed(job.errorCode());
                    } else {
                        scheduleRetry(letter);
                    }
                }));

        if (shouldRetryGeneration[0]) {
            try {
                orchestrationService.ensureLetterGenerationStarted(retrySeasonPk[0]);
            } catch (Exception e) {
                log.warn("letter retry trigger failed, will retry on next sweep: letterId={}", letterId, e);
            }
        }
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

    private void scheduleRetry(PartnerLetter letter) {
        int attempts = letter.getPollAttempts() + 1;
        Duration backoff = computeBackoff(attempts);
        letter.markPending(AiJobStatus.RUNNING, LocalDateTime.now().plus(backoff), attempts);
    }

    /** 1초부터 시작해 최대 5초까지 지수 backoff (요구사항 5.3). */
    private Duration computeBackoff(int attempts) {
        long seconds = Math.min(
                properties.polling().initialInterval().toSeconds() * (1L << Math.min(attempts, 10)),
                properties.polling().maxInterval().toSeconds());
        return Duration.ofSeconds(Math.max(seconds, properties.polling().initialInterval().toSeconds()));
    }
}
