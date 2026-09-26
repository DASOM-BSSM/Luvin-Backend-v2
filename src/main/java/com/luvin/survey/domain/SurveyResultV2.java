package com.luvin.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 설문 채점/분류 결과 하나(불변). 재설문은 이 row를 덮어쓰지 않고 새 row를 추가 저장한다 —
 * "최신"은 users.latest_survey_result_id가 가리키는 것만 의미한다.
 *
 * core_scores/auxiliary_scores/distances/explanation_evidence는 Python reference와 동일한
 * classifier.py classify() 출력 shape을 그대로 JSON 문자열로 저장한다(재계산 없이 그대로 재현 가능해야
 * 하므로 반올림 이전 원본 S/W는 저장하지 않고, evidence_weight/observations/direction_conflict가
 * 포함된 표시값 그대로 둔다 — 원본 답변은 survey_submission_answers에 남아있어 필요시 재계산 가능하다).
 */
@Entity
@Table(name = "survey_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResultV2 {

    @Id
    @Column(name = "id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private SurveySubmissionV2 submission;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "core_scores", nullable = false, columnDefinition = "jsonb")
    private String coreScoresJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auxiliary_scores", nullable = false, columnDefinition = "jsonb")
    private String auxiliaryScoresJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_stats", nullable = false, columnDefinition = "jsonb")
    private String evidenceStatsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "distances", nullable = false, columnDefinition = "jsonb")
    private String distancesJson;

    @Column(name = "primary_type", nullable = false, length = 50)
    private String primaryType;

    @Column(name = "secondary_type", nullable = false, length = 50)
    private String secondaryType;

    @Column(name = "mixed", nullable = false)
    private boolean mixed;

    @Column(name = "poor_fit", nullable = false)
    private boolean poorFit;

    @Column(name = "tie", nullable = false)
    private boolean tie;

    @Column(name = "top_two_gap", nullable = false, precision = 12, scale = 6)
    private BigDecimal topTwoGap;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation_evidence", nullable = false, columnDefinition = "jsonb")
    private String explanationEvidenceJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SurveyResultV2(UUID id, SurveySubmissionV2 submission,
                           String coreScoresJson, String auxiliaryScoresJson, String evidenceStatsJson,
                           String distancesJson, String primaryType, String secondaryType,
                           boolean mixed, boolean poorFit, boolean tie, BigDecimal topTwoGap,
                           String explanationEvidenceJson) {
        this.id = id;
        this.submission = submission;
        this.coreScoresJson = coreScoresJson;
        this.auxiliaryScoresJson = auxiliaryScoresJson;
        this.evidenceStatsJson = evidenceStatsJson;
        this.distancesJson = distancesJson;
        this.primaryType = primaryType;
        this.secondaryType = secondaryType;
        this.mixed = mixed;
        this.poorFit = poorFit;
        this.tie = tie;
        this.topTwoGap = topTwoGap;
        this.explanationEvidenceJson = explanationEvidenceJson;
        this.createdAt = LocalDateTime.now();
    }
}
