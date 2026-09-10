package test.luvin_backend_v2.dailyquestion.dto;

import com.luvin_backend_v2.dailyquestion.DailyQuestion;
import java.util.List;

public class DailyQuestionTodayResponse {

    private final Long questionId;
    private final String question;
    private final List<DailyQuestionOptionResponse> options;
    private final boolean answered;
    private final Long selectedOptionId;
    private final List<DailyQuestionOptionResultResponse> results;
    private final Long totalCount;

    public DailyQuestionTodayResponse(Long questionId, String question,
                                      List<DailyQuestionOptionResponse> options,
                                      boolean answered, Long selectedOptionId,
                                      List<DailyQuestionOptionResultResponse> results,
                                      Long totalCount) {
        this.questionId = questionId;
        this.question = question;
        this.options = options;
        this.answered = answered;
        this.selectedOptionId = selectedOptionId;
        this.results = results;
        this.totalCount = totalCount;
    }

    public static DailyQuestionTodayResponse ofUnanswered(DailyQuestion dailyQuestion,
                                                          List<DailyQuestionOptionResponse> options) {
        return new DailyQuestionTodayResponse(
                dailyQuestion.getDailyQuestionId(), dailyQuestion.getContent(),
                options, false, null, null, null);
    }

    public static DailyQuestionTodayResponse ofAnswered(DailyQuestion dailyQuestion,
                                                        List<DailyQuestionOptionResponse> options,
                                                        Long selectedOptionId,
                                                        List<DailyQuestionOptionResultResponse> results,
                                                        long totalCount) {
        return new DailyQuestionTodayResponse(
                dailyQuestion.getDailyQuestionId(), dailyQuestion.getContent(),
                options, true, selectedOptionId, results, totalCount);
    }

    public Long getQuestionId() { return questionId; }
    public String getQuestion() { return question; }
    public List<DailyQuestionOptionResponse> getOptions() { return options; }
    public boolean isAnswered() { return answered; }
    public Long getSelectedOptionId() { return selectedOptionId; }
    public List<DailyQuestionOptionResultResponse> getResults() { return results; }
    public Long getTotalCount() { return totalCount; }
}