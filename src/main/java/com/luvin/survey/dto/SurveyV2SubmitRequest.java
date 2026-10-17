package com.luvin.survey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SurveyV2SubmitRequest(
        @NotNull UUID definitionId,
        @NotBlank String surveyVersion,
        @NotNull UUID clientSubmissionId,
        @NotNull @Size(min = 20, max = 20) @Valid List<AnswerItem> answers
) {
    public record AnswerItem(@NotBlank String questionId, @NotBlank String answerId) {
    }
}
