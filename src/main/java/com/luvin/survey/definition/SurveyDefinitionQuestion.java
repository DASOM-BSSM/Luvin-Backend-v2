package com.luvin.survey.definition;

import java.util.List;

public record SurveyDefinitionQuestion(
        String id,
        String text,
        String primaryDimension,
        List<SurveyDefinitionAnswer> answers
) {
    public SurveyDefinitionAnswer answerById(String answerId) {
        return answers.stream().filter(a -> a.id().equals(answerId)).findFirst().orElse(null);
    }
}
