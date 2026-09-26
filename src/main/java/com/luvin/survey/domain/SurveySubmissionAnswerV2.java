package com.luvin.survey.domain;

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

/** 제출 하나에 속한 답변 20개 중 하나. (submission_id, question_code) unique로 문항당 정확히 1개를 강제한다. */
@Entity
@Table(name = "survey_submission_answers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"submission_id", "question_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveySubmissionAnswerV2 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private SurveySubmissionV2 submission;

    @Column(name = "question_code", nullable = false, length = 20)
    private String questionCode;

    @Column(name = "answer_code", nullable = false, length = 20)
    private String answerCode;

    public SurveySubmissionAnswerV2(SurveySubmissionV2 submission, String questionCode, String answerCode) {
        this.submission = submission;
        this.questionCode = questionCode;
        this.answerCode = answerCode;
    }
}
