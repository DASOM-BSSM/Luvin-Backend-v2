package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EP5(시즌 마무리) 완료 시 최종 매칭 상대가 보내는 "SECRET LOVE" 쪽지. 시즌당 정확히 1개만
 * 존재한다(season_id unique). AI 서비스가 POST/GET /v1/seasons/{season_id}/letter로 비동기
 * 생성하므로(요청 시점에는 아직 본문이 없다), job 상태 추적 컬럼은 ai_episode_progresses/
 * ai_reroll_requests와 동일한 컨벤션을 따른다 — partnerId/message/renderMode는 job이
 * SUCCEEDED된 뒤에만 채워진다.
 *
 * attempts는 "실제로 새 job_id를 받은 횟수"만 센다(AI가 REPORT_REQUIRED 등으로 job 자체를
 * 만들어주지 않은 시도는 포함하지 않는다) — 요구사항의 "시즌당 3번까지"를 AI 서비스의 자체
 * 집계(LETTER_ATTEMPTS_EXHAUSTED)에만 맡기지 않고 Spring 쪽에서도 이중으로 보장한다.
 */
@Entity
@Table(name = "partner_letters", uniqueConstraints = @UniqueConstraint(columnNames = {"season_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerLetter {

    public static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "partner_id", columnDefinition = "uuid")
    private UUID partnerId;

    @Column(name = "message", length = 2000)
    private String message;

    @Column(name = "render_mode", length = 50)
    private String renderMode;

    @Column(name = "job_id", columnDefinition = "uuid")
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", length = 20)
    private AiJobStatus jobStatus;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    /** 다음 polling(또는 FAILED 재시도) 예정 시각. */
    @Column(name = "next_poll_at")
    private LocalDateTime nextPollAt;

    @Column(name = "poll_attempts", nullable = false)
    private int pollAttempts = 0;

    @Version
    @Column(name = "jpa_version", nullable = false)
    private Long jpaVersion;

    public PartnerLetter(Long memberId, AiSeason season) {
        this.memberId = memberId;
        this.season = season;
        this.createdAt = LocalDateTime.now();
    }

    /** POST /letter가 새 job을 접수했을 때. FAILED 이후 재시도도 같은 row를 재사용한다. */
    public void markJobAccepted(UUID jobId) {
        this.jobId = jobId;
        this.jobStatus = AiJobStatus.QUEUED;
        this.errorCode = null;
        this.attempts += 1;
        LocalDateTime now = LocalDateTime.now();
        this.requestedAt = now;
        this.nextPollAt = now;
        this.pollAttempts = 0;
    }

    public void markPending(AiJobStatus status, LocalDateTime nextPollAt, int pollAttempts) {
        this.jobStatus = status;
        this.nextPollAt = nextPollAt;
        this.pollAttempts = pollAttempts;
    }

    public void markSucceeded(UUID partnerId, String message, String renderMode) {
        this.jobStatus = AiJobStatus.SUCCEEDED;
        this.partnerId = partnerId;
        this.message = message;
        this.renderMode = renderMode;
        this.errorCode = null;
        this.nextPollAt = null;
    }

    public void markFailed(String errorCode) {
        this.jobStatus = AiJobStatus.FAILED;
        this.errorCode = errorCode;
        this.nextPollAt = null;
    }

    /** 실패 재시도 접수 자체가 지연/거절됐을 때 다음 tick에 바로 또 집히지 않도록 미룬다. */
    public void postponeRetry(LocalDateTime nextPollAt) {
        this.nextPollAt = nextPollAt;
    }

    public void markRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }

    public boolean isGenerationAccepted() {
        return jobId != null;
    }

    public boolean hasReachedMaxAttempts() {
        return attempts >= MAX_ATTEMPTS;
    }
}
