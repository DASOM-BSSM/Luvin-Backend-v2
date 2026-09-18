package com.luvin.dailyquestion.dto;

public class DailyQuestionAnswerRequest {

    private Long selectedOption;

    public DailyQuestionAnswerRequest() {
    }

    public Long getSelectedOption() { return selectedOption; }
    public void setSelectedOption(Long selectedOption) { this.selectedOption = selectedOption; }
}