package com.luvin.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 설문 제출 하나. (member_id, client_submission_id) unique로 재시도 멱등성을 DB 레벨에서도 보장한다.
 * requestHash는 정의/버전+정렬된 answerId 목록으로 만든 canonical hash — 같은 key라도 내용이 다르면
 * 409로 거부해야 하므로 별도 저장한다.
 */
@Entity
@Table(name = "survey_submissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "client_submission_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveySubmissionV2 {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private SurveyDefinitionV2 definition;

    @Column(name = "client_submission_id", nullable = false)
    private UUID clientSubmissionId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    public SurveySubmissionV2(UUID id, Long memberId, SurveyDefinitionV2 definition,
                               UUID clientSubmissionId, String requestHash) {
        this.id = id;
        this.memberId = memberId;
        this.definition = definition;
        this.clientSubmissionId = clientSubmissionId;
        this.requestHash = requestHash;
        this.submittedAt = LocalDateTime.now();
    }
}
