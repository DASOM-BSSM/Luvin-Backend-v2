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
 * memberId당 여러 시즌(과거 시즌 포함)을 가질 수 있다 — seasonNumber(1부터)로 순서를 구분하고,
 * (member_id, season_number)가 유니크하다. "현재 시즌"은 seasonNumber가 가장 큰 행이다.
 *
 * 주의: {@code revision}은 AI 서비스가 관리하는 낙관적 동시성 token이고,
 * {@code jpaVersion}은 이 row 자체에 대한 Spring 쪽 낙관적 락(동시 polling/갱신 경합 방지)이다. 서로 다른 개념이다.
 */
@Entity
@Table(name = "ai_seasons", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "season_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSeason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 그 사용자의 몇 번째 시즌인지(1부터). GET /api/ai/seasons 응답과 "다음 시즌" 계산의 기준이 된다. */
    @Column(name = "season_number", nullable = false)
    private Integer seasonNumber;

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

    /** complete() 호출 시각. GET /api/ai/seasons 목록 응답에 노출한다. 완료 전에는 null. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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

    public AiSeason(Long memberId, Integer seasonNumber, UUID seasonId, Integer revision, Integer currentEpisode,
                     UUID surveyResultId) {
        this.memberId = memberId;
        this.seasonNumber = seasonNumber;
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

    /**
     * 지난 시즌의 report를 다시 조회해도(seasonId 지정 조회) completedAt이 매번 "지금"으로
     * 덮어써지지 않도록 이미 COMPLETED면 아무것도 하지 않는다.
     */
    public void complete() {
        if (this.status == AiSeasonStatus.COMPLETED) {
            return;
        }
        this.status = AiSeasonStatus.COMPLETED;
        LocalDateTime now = LocalDateTime.now();
        this.updatedAt = now;
        this.completedAt = now;
    }
}
