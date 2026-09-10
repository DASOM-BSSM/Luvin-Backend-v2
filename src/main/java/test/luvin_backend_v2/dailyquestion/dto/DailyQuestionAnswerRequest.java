package test.luvin_backend_v2.dailyquestion.dto;

public class DailyQuestionAnswerRequest {

    private Long selectedOption;

    public DailyQuestionAnswerRequest() {
    }

    public Long getSelectedOption() { return selectedOption; }
    public void setSelectedOption(Long selectedOption) { this.selectedOption = selectedOption; }
}