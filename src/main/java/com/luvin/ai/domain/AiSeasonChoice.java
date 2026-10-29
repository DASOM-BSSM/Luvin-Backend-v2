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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 3·5·6·8화 끝 선택(①~④) 기록. 선택 지점당 1번만 선택할 수 있다(season_id + point_id unique).
 * 판정 사유(reasonCode)는 10화 인터뷰룸 생성 입력으로 쓴다.
 */
@Entity
@Table(name = "ai_season_choices", uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "point_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSeasonChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private AiSeason season;

    @Column(name = "point_id", nullable = false)
    private Integer pointId;

    @Column(name = "episode_order", nullable = false)
    private Integer episodeOrder;

    /** null이면 "혼자 있기". */
    @Column(name = "bread_id", columnDefinition = "uuid")
    private UUID breadId;

    @Column(name = "accepted", nullable = false)
    private boolean accepted;

    @Column(name = "reason_code", nullable = false, length = 40)
    private String reasonCode;

    /**
     * 선택 결과 장면(choice_result 블록, 거절이면 대체 장면 포함) JSON 배열. 같은 선택을 다시 보내면 그대로 돌려준다.
     * 결과 장면은 선택을 저장한 뒤 백그라운드에서 만들므로 만드는 동안(GENERATING)과 실패(FAILED)면 null이다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_blocks", columnDefinition = "jsonb")
    private String resultBlocksJson;

    /** 결과 장면 생성 상태. V16 전의 선택은 결과까지 저장된 것이라 READY(null도 READY로 본다). */
    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", length = 20)
    private AiStoryContentStatus resultStatus;

    /** 결과 장면 생성 회차. 실패 뒤 다시 보내면 1 늘린다 — 늦게 끝난 이전 회차 결과는 저장하지 않는다. */
    @Column(name = "result_round")
    private Integer resultRound;

    @Column(name = "result_requested_at")
    private LocalDateTime resultRequestedAt;

    @Column(name = "result_error_code", length = 100)
    private String resultErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AiSeasonChoice(Long memberId, AiSeason season, int pointId, int episodeOrder, UUID breadId,
                          boolean accepted, String reasonCode, String resultBlocksJson) {
        this.memberId = memberId;
        this.season = season;
        this.pointId = pointId;
        this.episodeOrder = episodeOrder;
        this.breadId = breadId;
        this.accepted = accepted;
        this.reasonCode = reasonCode;
        this.resultBlocksJson = resultBlocksJson;
        this.createdAt = LocalDateTime.now();
        this.resultRound = 1;
        this.resultRequestedAt = this.createdAt;
        this.resultStatus = resultBlocksJson != null ? AiStoryContentStatus.READY : AiStoryContentStatus.GENERATING;
    }

    /** null은 V16 전의 선택(결과까지 저장됨)이다. */
    public AiStoryContentStatus effectiveResultStatus() {
        return resultStatus == null ? AiStoryContentStatus.READY : resultStatus;
    }

    public int currentResultRound() {
        return resultRound == null ? 1 : resultRound;
    }

    /** 실패했거나 멈춘 결과 장면 생성을 새 회차로 다시 시작한다. */
    public void restartResult() {
        this.resultStatus = AiStoryContentStatus.GENERATING;
        this.resultRound = currentResultRound() + 1;
        this.resultRequestedAt = LocalDateTime.now();
        this.resultErrorCode = null;
    }

    public void markResultReady(String resultBlocksJson) {
        this.resultBlocksJson = resultBlocksJson;
        this.resultStatus = AiStoryContentStatus.READY;
        this.resultErrorCode = null;
    }

    public void markResultFailed(String errorCode) {
        this.resultStatus = AiStoryContentStatus.FAILED;
        this.resultErrorCode = errorCode;
    }

    public boolean isAlone() {
        return breadId == null;
    }
}
