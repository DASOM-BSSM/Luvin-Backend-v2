package com.luvin.common.exception;

public class SurveyNotFoundException extends RuntimeException {
    public SurveyNotFoundException(Long surveyId) {
        super("설문을 찾을 수 없습니다. surveyId=" + surveyId);
    }
}