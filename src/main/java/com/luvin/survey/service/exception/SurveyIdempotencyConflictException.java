package com.luvin.survey.service.exception;

/** 같은 clientSubmissionId로 이전과 다른 내용(payload hash)을 다시 제출했을 때. */
public class SurveyIdempotencyConflictException extends RuntimeException {
    public SurveyIdempotencyConflictException() {
        super("이미 처리된 제출과 내용이 다릅니다. 같은 clientSubmissionId로는 같은 답변만 재전송할 수 있습니다.");
    }
}
