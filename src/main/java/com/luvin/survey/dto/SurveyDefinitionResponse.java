package com.luvin.survey.dto;

import java.util.List;
import java.util.UUID;

public record SurveyDefinitionResponse(
        UUID definitionId,
        String surveyVersion,
        String scoringVersion,
        String classificationVersion,
        String title,
        int questionCount,
        List<QuestionItem> questions
) {
    public record QuestionItem(String questionId, int order, String text, List<AnswerItem> answers) {
    }

    public record AnswerItem(String answerId, int order, String text) {
    }
}
