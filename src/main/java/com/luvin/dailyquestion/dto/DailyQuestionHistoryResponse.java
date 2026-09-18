package com.luvin.dailyquestion.dto;

import com.luvin.dailyquestion.domain.DailyQuestionAnswer;

public class DailyQuestionHistoryResponse {

    private final String question;
    private final String answer;

    public DailyQuestionHistoryResponse(String question, String answer) {
        this.question = question;
        this.answer = answer;
    }

    public static DailyQuestionHistoryResponse from(DailyQuestionAnswer answer) {
        return new DailyQuestionHistoryResponse(
                answer.getDailyQuestion().getContent(),
                answer.getSelectedOption().getContent());
    }

    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
}
