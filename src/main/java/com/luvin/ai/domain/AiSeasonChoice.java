package com.luvin.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    /** 선택 결과 장면(choice_result 블록, 거절이면 대체 장면 포함) JSON 배열. 같은 선택을 다시 보내면 그대로 돌려준다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_blocks", nullable = false, columnDefinition = "jsonb")
    private String resultBlocksJson;

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
    }

    public boolean isAlone() {
        return breadId == null;
    }
}
