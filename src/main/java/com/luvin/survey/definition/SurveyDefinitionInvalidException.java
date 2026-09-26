package com.luvin.survey.definition;

/** config JSON이 구조적 불변식(문항 20개, 유일 ID, 프로필 8개 등)을 만족하지 않을 때. */
public class SurveyDefinitionInvalidException extends RuntimeException {
    public SurveyDefinitionInvalidException(String message) {
        super(message);
    }
}
