package com.luvin.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
 * 설문 v2 definition 하나(불변). surveyVersion/scoringVersion/classificationVersion 조합으로
 * 유일하며, 셋 중 무엇이 바뀌어도 새 row(=새 definitionId)를 만든다. configJson은
 * {@link com.luvin.survey.definition.SurveyDefinitionConfig}를 그대로 직렬화한 원문이다.
 */
@Entity
@Table(name = "survey_definitions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"survey_version", "scoring_version", "classification_version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyDefinitionV2 {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "survey_version", nullable = false, length = 100)
    private String surveyVersion;

    @Column(name = "scoring_version", nullable = false, length = 100)
    private String scoringVersion;

    @Column(name = "classification_version", nullable = false, length = 100)
    private String classificationVersion;

    @Column(name = "config_hash", nullable = false, length = 64)
    private String configHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private String configJson;

    /** active(현재 공개) / accepted(제출 계속 허용) / retired(410) 중 하나. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SurveyDefinitionV2(UUID id, String surveyVersion, String scoringVersion, String classificationVersion,
                               String configHash, String configJson, String status) {
        this.id = id;
        this.surveyVersion = surveyVersion;
        this.scoringVersion = scoringVersion;
        this.classificationVersion = classificationVersion;
        this.configHash = configHash;
        this.configJson = configJson;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isRetired() {
        return "retired".equals(status);
    }
}
