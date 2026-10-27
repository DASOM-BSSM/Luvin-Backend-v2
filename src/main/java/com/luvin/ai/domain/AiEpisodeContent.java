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

/**
 * 장면 블록 회차 콘텐츠. 한 번 생성하면 저장해서 재사용한다(season_id + episode_order unique).
 * 생성은 GET content 요청이 시작하고 백그라운드에서 끝난다 — 그동안 status가 GENERATING이다.
 * summaryJson은 회차 완료 시 정리 단계가 돌려준 회차 요약으로, 쪽지·최종 리포트 생성 재료로 쓴다.
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
    }

    /** FAILED였거나 생성이 너무 오래 멈춰 있던 회차를 다시 생성한다. */
    public void restartGeneration(AiStoryRoute route) {
        this.route = route;
        this.status = AiStoryContentStatus.GENERATING;
        this.errorCode = null;
        this.failureReportedAt = null;
        this.requestedAt = LocalDateTime.now();
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

    public boolean isReady() {
        return status == AiStoryContentStatus.READY;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }
}
