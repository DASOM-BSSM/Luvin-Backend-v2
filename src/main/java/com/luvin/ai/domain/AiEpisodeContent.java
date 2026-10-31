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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 장면 블록 회차 콘텐츠. 한 번 생성하면 저장해서 재사용한다(season_id + episode_order unique).
 * 생성은 GET content 요청이 시작하고 백그라운드에서 끝난다 — 그동안 status가 GENERATING이다.
 * summaryJson은 회차 완료 시 정리 단계가 돌려준 회차 요약으로, 쪽지·최종 리포트 생성 재료로 쓴다.
 * 회차 완료(정리·쪽지·리포트 생성)도 POST complete가 시작하고 백그라운드에서 끝난다 — 그동안 completing이다.
 */
@Entity
@Table(name = "ai_episode_contents", uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "episode_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiEpisodeContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "episode_order", nullable = false)
    private Integer episodeOrder;

    /** 7~10화만 값이 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "route", length = 20)
    private AiStoryRoute route;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiStoryContentStatus status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "intro", columnDefinition = "jsonb")
    private String introJson;

    /** 1화만 값이 있다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stage_intro", columnDefinition = "jsonb")
    private String stageIntroJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blocks", columnDefinition = "jsonb")
    private String blocksJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb")
    private String summaryJson;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * FAILED를 앱에 처음 응답한 시각. null이면 아직 아무 GET content도 failed를 받지 못했다 — 그 GET은 다시 생성하지
     * 않고 failed를 그대로 돌려준다. 같은 GET이 폴링이자 재시도라, 이 구분이 없으면 폴링이 failed를 보기 전에 매번
     * 재생성을 시작해 앱이 generating에서 빠져나오지 못한다.
     */
    @Column(name = "failure_reported_at")
    private LocalDateTime failureReportedAt;

    /** 회차 완료를 백그라운드에서 시작한 시각. 완료되면 completedAt이 생긴다. */
    @Column(name = "completion_requested_at")
    private LocalDateTime completionRequestedAt;

    /** 회차 완료가 실패한 오류 코드. 다시 완료를 요청하면 지운다. */
    @Column(name = "completion_error_code", length = 100)
    private String completionErrorCode;

    /**
     * 생성 회차 번호. 실패 뒤 다시 생성하면 올라가고, 멈춘 생성을 다시 시작할 때는 그대로다(같은 AI job을 이어서 본다).
     * 결과는 같은 회차일 때만 저장해, 늦게 끝난 이전 회차 결과가 새 생성을 덮어쓰지 않게 한다. 이전 row는 null(0으로 본다).
     */
    @Column(name = "generation_round")
    private Integer generationRound;

    /** 생성 중인 단계(stage-intro | episode | interview). 앱 폴링 응답의 진행 표시용. */
    @Column(name = "generation_stage", length = 30)
    private String generationStage;

    /** 지금 단계의 AI 생성 job id(비동기 job으로 부를 때). */
    @Column(name = "generation_job_id", columnDefinition = "uuid")
    private UUID generationJobId;

    /** AI가 첫 시도를 검증에서 버리고 다시 쓰는 중이다. */
    @Column(name = "generation_regenerating")
    private Boolean generationRegenerating;

    /** 회차 완료 회차 번호. 규칙은 generationRound와 같다. */
    @Column(name = "completion_round")
    private Integer completionRound;

    /** 완료 중인 단계(summarize | episode-note | season-report). */
    @Column(name = "completion_stage", length = 30)
    private String completionStage;

    @Column(name = "completion_job_id", columnDefinition = "uuid")
    private UUID completionJobId;

    @Column(name = "completion_regenerating")
    private Boolean completionRegenerating;

    @Version
    @Column(name = "jpa_version", nullable = false)
    private Long jpaVersion;

    public AiEpisodeContent(Long memberId, AiSeason season, int episodeOrder, AiStoryRoute route) {
        this.memberId = memberId;
        this.season = season;
        this.episodeOrder = episodeOrder;
        this.route = route;
        this.status = AiStoryContentStatus.GENERATING;
        this.requestedAt = LocalDateTime.now();
        this.generationRound = 1;
    }

    /**
     * FAILED였거나 생성이 너무 오래 멈춰 있던 회차를 다시 생성한다. 실패 뒤면 새 회차(새 AI job), 멈춘 것이면 같은 회차다.
     */
    public void restartGeneration(AiStoryRoute route) {
        if (status == AiStoryContentStatus.FAILED) {
            this.generationRound = currentGenerationRound() + 1;
        }
        this.route = route;
        this.status = AiStoryContentStatus.GENERATING;
        this.errorCode = null;
        this.failureReportedAt = null;
        this.requestedAt = LocalDateTime.now();
        this.generationStage = null;
        this.generationJobId = null;
        this.generationRegenerating = null;
    }

    public int currentGenerationRound() {
        return generationRound == null ? 0 : generationRound;
    }

    public int currentCompletionRound() {
        return completionRound == null ? 0 : completionRound;
    }

    public void markReady(String introJson, String stageIntroJson, String blocksJson) {
        this.introJson = introJson;
        this.stageIntroJson = stageIntroJson;
        this.blocksJson = blocksJson;
        this.status = AiStoryContentStatus.READY;
        this.errorCode = null;
        this.generatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorCode) {
        this.status = AiStoryContentStatus.FAILED;
        this.errorCode = errorCode;
        this.failureReportedAt = null;
    }

    /**
     * 앱이 기다리지 않던 생성(다음 회차 미리 생성)의 실패. 이미 알린 실패로 남겨, 다음 GET content가 실패를 보여 주지 않고
     * 바로 다시 생성하게 한다.
     */
    public void markFailedUnseen(String errorCode) {
        markFailed(errorCode);
        this.failureReportedAt = LocalDateTime.now();
    }

    /** 이 실패를 앱에 처음 알린다. 이미 알린 실패면 false — 호출부는 다시 생성한다. */
    public boolean reportFailureOnce() {
        if (failureReportedAt != null) {
            return false;
        }
        this.failureReportedAt = LocalDateTime.now();
        return true;
    }

    public void markCompleted(String summaryJson) {
        this.summaryJson = summaryJson;
        this.completedAt = LocalDateTime.now();
    }

    /** 처음이거나 실패 뒤면 새 완료 회차(새 AI job), 멈춘 완료를 다시 시작하는 것이면 같은 회차다. */
    public void beginCompletion() {
        if (completionRequestedAt == null || completionErrorCode != null) {
            this.completionRound = currentCompletionRound() + 1;
        }
        this.completionRequestedAt = LocalDateTime.now();
        this.completionErrorCode = null;
        this.completionStage = null;
        this.completionJobId = null;
        this.completionRegenerating = null;
    }

    public void markCompletionFailed(String errorCode) {
        if (!isCompleted()) {
            this.completionErrorCode = errorCode;
        }
    }

    /** 완료를 시작했고 아직 끝나지도, 실패하지도 않았다. */
    public boolean isCompleting() {
        return !isCompleted() && completionRequestedAt != null && completionErrorCode == null;
    }

    public boolean isReady() {
        return status == AiStoryContentStatus.READY;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }
}
