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
import java.util.Objects;
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

    /**
     * 장면 블록 회차(1~10화) 흐름의 시즌 배경. null이면 아직 새 흐름을 시작하지 않은 시즌이다(기존 5화
     * 흐름으로 진행한 시즌 포함). 처음 정해진 뒤에는 시즌 동안 바꾸지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "setting", length = 20)
    private AiSeasonSetting setting;

    /** 새 흐름에서 지금 진행할 회차(1~10). AI가 관리하는 currentEpisode(기존 5화 흐름)와 별개다. */
    @Column(name = "current_order")
    private Integer currentOrder;

    /** 6화 선택(③) 전에는 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "route", length = 20)
    private AiStoryRoute route;

    @Column(name = "romance_partner_id", columnDefinition = "uuid")
    private UUID romancePartnerId;

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

    public boolean isStoryStarted() {
        return setting != null;
    }

    /** 새 흐름을 시작한다. 이미 배경이 정해졌으면 바꾸지 않는다. */
    public void startStory(AiSeasonSetting setting) {
        if (this.setting != null) {
            return;
        }
        this.setting = setting;
        this.currentOrder = 1;
        this.updatedAt = LocalDateTime.now();
    }

    /** 회차 완료 시 다음 회차로 넘어간다. 이미 더 진행했으면 되돌리지 않는다. */
    public void advanceOrder(int nextOrder) {
        if (this.currentOrder == null || nextOrder > this.currentOrder) {
            this.currentOrder = nextOrder;
            this.updatedAt = LocalDateTime.now();
        }
    }

    public void applyRoute(AiStoryRoute route, UUID romancePartnerId) {
        this.route = route;
        this.romancePartnerId = romancePartnerId;
        this.updatedAt = LocalDateTime.now();
    }

    /** 개발 전용 초기화. 시즌을 새 흐름 시작 전 상태(진행 중)로 되돌린다. */
    public void resetStory() {
        this.setting = null;
        this.currentOrder = null;
        this.route = null;
        this.romancePartnerId = null;
        this.status = AiSeasonStatus.IN_PROGRESS;
        this.completedAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 값이 바뀔 때만 쓴다. 시즌 상태 조회(GET /me)가 폴링마다 부르는데, 매번 쓰면 row 버전이 올라가 같은 시즌을 저장하는
     * 백그라운드 작업(회차 완료 등)이 낙관적 락 충돌로 실패한다.
     */
    public void syncFromAi(Integer revision, Integer currentEpisode) {
        if (Objects.equals(this.revision, revision) && Objects.equals(this.currentEpisode, currentEpisode)) {
            return;
        }
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
