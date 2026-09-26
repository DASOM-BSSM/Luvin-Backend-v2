package com.luvin.survey.service.exception;

/** from-survey 시즌 생성 시 아직 유효한 설문 결과가 없을 때. */
public class SurveyRequiredException extends RuntimeException {
    public SurveyRequiredException() {
        super("먼저 설문을 완료해야 합니다.");
    }
}
