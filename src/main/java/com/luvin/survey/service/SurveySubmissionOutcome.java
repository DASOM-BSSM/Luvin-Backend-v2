package com.luvin.survey.service;

import com.luvin.survey.dto.SurveyResultResponse;

/** created=true면 신규 채점(201), false면 같은 clientSubmissionId의 기존 결과 replay(200). */
public record SurveySubmissionOutcome(SurveyResultResponse result, boolean created) {
}
