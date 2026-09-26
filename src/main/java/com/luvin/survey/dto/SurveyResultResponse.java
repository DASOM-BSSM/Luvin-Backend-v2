package com.luvin.survey.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SurveyResultResponse(
        UUID resultId,
        UUID clientSubmissionId,
        UUID definitionId,
        String surveyVersion,
        String scoringVersion,
        String classificationVersion,
        String primaryType,
        String primaryLabel,
        String secondaryType,
        boolean mixed,
        boolean poorFit,
        boolean tie,
        String displayName,
        String summary,
        List<String> reasonTexts,
        LocalDateTime completedAt
) {
}
