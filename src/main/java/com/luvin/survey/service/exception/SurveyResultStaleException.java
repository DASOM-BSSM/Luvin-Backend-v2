package com.luvin.survey.service.exception;

/**
 * from-survey 시즌 생성 요청의 surveyResultId가 사용자의 "현재 최신 결과"와 다르고, 아직 생성 입력
 * snapshot도 저장돼 있지 않을 때. 최신 결과로 다시 조회하라는 뜻이며, 서버가 임의로 최신 결과로
 * 바꿔치기하지 않는다.
 */
public class SurveyResultStaleException extends RuntimeException {
    public SurveyResultStaleException() {
        super("요청한 설문 결과가 더 이상 최신이 아닙니다. 최신 결과를 다시 확인해 주세요.");
    }
}
