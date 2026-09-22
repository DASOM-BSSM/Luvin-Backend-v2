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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * idempotency key, 기존 version, 요청 상대, 결과 job을 저장한다 (요구사항 4절).
 */
@Entity
@Table(name = "ai_reroll_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRerollRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    @Column(name = "idempotency_key", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID idempotencyKey;

    @Column(name = "previous_version_id", nullable = false, columnDefinition = "uuid")
    private UUID previousVersionId;

    @Column(name = "requested_partner_id", nullable = false, columnDefinition = "uuid")
    private UUID requestedPartnerId;

    @Column(name = "job_id", columnDefinition = "uuid")
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", length = 20)
    private AiJobStatus jobStatus;

    @Column(name = "result_version_id", columnDefinition = "uuid")
    private UUID resultVersionId;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "next_poll_at")
    private LocalDateTime nextPollAt;

    @Column(name = "poll_attempts", nullable = false)
    private Integer pollAttempts = 0;

    public AiRerollRequest(AiSeason season, Integer episodeNumber, UUID idempotencyKey,
                            UUID previousVersionId, UUID requestedPartnerId, UUID jobId) {
        this.season = season;
        this.episodeNumber = episodeNumber;
        this.idempotencyKey = idempotencyKey;
        this.previousVersionId = previousVersionId;
        this.requestedPartnerId = requestedPartnerId;
        this.jobId = jobId;
        this.jobStatus = AiJobStatus.QUEUED;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.nextPollAt = now;
    }

    public void markPending(AiJobStatus status, LocalDateTime nextPollAt, int pollAttempts) {
        this.jobStatus = status;
        this.nextPollAt = nextPollAt;
        this.pollAttempts = pollAttempts;
        this.updatedAt = LocalDateTime.now();
    }

    public void markSucceeded(UUID resultVersionId) {
        this.jobStatus = AiJobStatus.SUCCEEDED;
        this.resultVersionId = resultVersionId;
        this.nextPollAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorCode) {
        this.jobStatus = AiJobStatus.FAILED;
        this.errorCode = errorCode;
        this.nextPollAt = null;
        this.updatedAt = LocalDateTime.now();
    }
}
