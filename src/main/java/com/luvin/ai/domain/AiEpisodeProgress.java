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
 * episode별 generation job_id/status, version_id, 마지막 표시 sequence를 저장한다 (요구사항 4절).
 * polling 재개와 중복 generation 방지, pagination 중 version 고정에 사용된다.
 */
@Entity
@Table(name = "ai_episode_progresses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "episode_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiEpisodeProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    @Column(name = "job_id", columnDefinition = "uuid")
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", length = 20)
    private AiJobStatus jobStatus;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    /** generation job 성공 시 result_version_id. pagination 중 이 값으로 고정한다. */
    @Column(name = "version_id", columnDefinition = "uuid")
    private UUID versionId;

    /** 앱 진행도 저장용. AI 조회 자체는 진행도를 변경하지 않는다 (요구사항 4절). */
    @Column(name = "last_seen_sequence", nullable = false)
    private Integer lastSeenSequence = 0;

    @Column(name = "topic_id", length = 100)
    private String topicId;

    @Column(name = "topic_title", length = 200)
    private String topicTitle;

    @Column(name = "topic_category", length = 100)
    private String topicCategory;

    @Column(name = "topic_config_version", length = 50)
    private String topicConfigVersion;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 다음 polling 예정 시각. 1초부터 시작해 최대 5초까지 backoff한다 (요구사항 5.3). */
    @Column(name = "next_poll_at")
    private LocalDateTime nextPollAt;

    @Column(name = "poll_attempts", nullable = false)
    private Integer pollAttempts = 0;

    @Version
    @Column(name = "jpa_version", nullable = false)
    private Long jpaVersion;

    public AiEpisodeProgress(AiSeason season, Integer episodeNumber, UUID jobId) {
        this.season = season;
        this.episodeNumber = episodeNumber;
        this.jobId = jobId;
        this.jobStatus = AiJobStatus.QUEUED;
        LocalDateTime now = LocalDateTime.now();
        this.requestedAt = now;
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
        this.versionId = resultVersionId;
        this.errorCode = null;
        this.nextPollAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorCode) {
        this.jobStatus = AiJobStatus.FAILED;
        this.errorCode = errorCode;
        this.nextPollAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateTopic(String topicId, String topicTitle, String topicCategory, String topicConfigVersion) {
        this.topicId = topicId;
        this.topicTitle = topicTitle;
        this.topicCategory = topicCategory;
        this.topicConfigVersion = topicConfigVersion;
    }

    public void advanceLastSeenSequence(int sequence) {
        if (sequence > this.lastSeenSequence) {
            this.lastSeenSequence = sequence;
            this.updatedAt = LocalDateTime.now();
        }
    }

    /** reroll 성공으로 active version이 바뀐 경우 pagination 재시작을 위해 호출한다. */
    public void replaceVersion(UUID newVersionId) {
        this.versionId = newVersionId;
        this.lastSeenSequence = 0;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isGenerationAccepted() {
        return jobId != null;
    }
}
