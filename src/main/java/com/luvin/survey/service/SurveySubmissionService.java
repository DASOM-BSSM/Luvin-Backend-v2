package com.luvin.survey.service;

import com.luvin.survey.dto.SurveyResultResponse;
import com.luvin.survey.dto.SurveyV2SubmitRequest;

import java.util.Optional;
import java.util.UUID;

public interface SurveySubmissionService {
    SurveySubmissionOutcome submit(Long memberId, SurveyV2SubmitRequest request);

    SurveyResultResponse getByClientSubmissionId(Long memberId, UUID clientSubmissionId);

    Optional<SurveyResultResponse> getLatestResult(Long memberId);
}
