package com.luvin.survey.scoring;

/**
 * 제출된 답변이 definition과 맞지 않을 때 (버전 불일치, 20개 미충족, 중복/알 수 없는 질문·답변 등).
 * code는 Python reference의 ValueError 메시지와 동일한 machine code를 사용한다.
 */
public class SurveyAnswerValidationException extends RuntimeException {

    private final String code;

    public SurveyAnswerValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
