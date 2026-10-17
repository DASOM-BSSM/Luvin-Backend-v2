package com.luvin.survey.service.exception;

/** 존재하지 않거나 본인 소유가 아닌 clientSubmissionId 조회. 어느 쪽인지 구분해 노출하지 않는다. */
public class SurveySubmissionNotFoundException extends RuntimeException {
    public SurveySubmissionNotFoundException() {
        super("제출 내역을 찾을 수 없습니다.");
    }
}
