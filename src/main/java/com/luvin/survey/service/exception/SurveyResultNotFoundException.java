package com.luvin.survey.service.exception;

/** 존재하지 않거나 본인 소유가 아닌 surveyResultId 조회. */
public class SurveyResultNotFoundException extends RuntimeException {
    public SurveyResultNotFoundException() {
        super("설문 결과를 찾을 수 없습니다.");
    }
}
