package com.luvin.ai.domain;

import com.luvin.survey.domain.SurveyResultV2;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 설문 결과 기반 시즌 생성의 입력 snapshot. member당 1개(unique)만 존재하며, 원격 AI 호출 전에
 * 먼저 저장해 idempotency key와 profile을 고정한다 — 사용자가 호출 직후 재설문해도 retry payload가
 * 바뀌지 않게 하기 위함이다 (BACKEND_CHANGES.md 9절).
 */
@Entity
@Table(name = "ai_season_creation_inputs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSeasonCreationInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_result_id", nullable = false)
    private SurveyResultV2 surveyResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_json", nullable = false, columnDefinition = "jsonb")
    private String profileJson;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    /** PENDING(원격 호출 전 저장 완료) / SUCCEEDED(AI 응답 반영 완료). */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AiSeasonCreationInput(Long memberId, SurveyResultV2 surveyResult, String profileJson,
                                  String requestHash, UUID idempotencyKey) {
        this.memberId = memberId;
        this.surveyResult = surveyResult;
        this.profileJson = profileJson;
        this.requestHash = requestHash;
        this.idempotencyKey = idempotencyKey;
        this.status = "PENDING";
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSucceeded() {
        this.status = "SUCCEEDED";
        this.updatedAt = LocalDateTime.now();
    }
}
