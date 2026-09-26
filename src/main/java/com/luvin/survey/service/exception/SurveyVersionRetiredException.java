package com.luvin.survey.service.exception;

/** 제출한 definitionId가 이미 retired 상태일 때(같은 key/hash replay가 아닌 새 제출인 경우). */
public class SurveyVersionRetiredException extends RuntimeException {
    public SurveyVersionRetiredException() {
        super("이 설문 버전은 더 이상 제출을 받지 않습니다. 새 definition으로 다시 시작해 주세요.");
    }
}
