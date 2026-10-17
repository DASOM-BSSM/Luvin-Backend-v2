package com.luvin.survey.service.exception;

/** 공개할 수 있는(active) definition이 없을 때. */
public class SurveyDefinitionUnavailableException extends RuntimeException {
    public SurveyDefinitionUnavailableException() {
        super("지금은 설문을 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }
}
