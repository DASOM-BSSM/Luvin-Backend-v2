package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring 사용자 ID ↔ AI season_id 연결과 mutation 낙관적 동시성 기준(revision)을 보관한다 (요구사항 4절).
 * memberId 당 하나의 활성 시즌만 존재한다고 가정한다 (동시에 여러 시즌 진행은 이 MVP 범위 밖).
 *
 * 주의: {@code revision}은 AI 서비스가 관리하는 낙관적 동시성 token이고,
 * {@code jpaVersion}은 이 row 자체에 대한 Spring 쪽 낙관적 락(동시 polling/갱신 경합 방지)이다. 서로 다른 개념이다.
 */
@Entity
@Table(name = "ai_seasons", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSeason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "season_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID seasonId;

    /** AI 서비스가 관리하는 낙관적 동시성 token. mutation마다 최신 값을 보내야 한다 (요구사항 6절). */
    @Column(name = "revision", nullable = false)
    private Integer revision;

    @Column(name = "current_episode", nullable = false)
    private Integer currentEpisode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiSeasonStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Spring 쪽 row 낙관적 락. AI의 revision과 별개다. */
    @Version
    @Column(name = "jpa_version", nullable = false)
    private Long jpaVersion;

    /**
     * 이 시즌을 생성할 때 쓴 {@code survey_results.id}. from-survey 경로로 만든 시즌만 값이 있고,
     * 레거시 POST /api/ai/seasons(원시 traits) 경로로 만든 시즌은 null이다 — 서버가 추측하지 않는다.
     */
    @Column(name = "survey_result_id")
    private UUID surveyResultId;

    public AiSeason(Long memberId, UUID seasonId, Integer revision, Integer currentEpisode) {
        this(memberId, seasonId, revision, currentEpisode, null);
    }

    public AiSeason(Long memberId, UUID seasonId, Integer revision, Integer currentEpisode, UUID surveyResultId) {
        this.memberId = memberId;
        this.seasonId = seasonId;
        this.revision = revision;
        this.currentEpisode = currentEpisode;
        this.status = AiSeasonStatus.IN_PROGRESS;
        this.surveyResultId = surveyResultId;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void syncFromAi(Integer revision, Integer currentEpisode) {
        this.revision = revision;
        this.currentEpisode = currentEpisode;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = AiSeasonStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }
}
