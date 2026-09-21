package com.luvin.survey.service;

import com.luvin.survey.dto.SurveyOptionResponse;
import com.luvin.survey.dto.SurveySubmitRequest;

public interface SurveyService {
    SurveyOptionResponse getQuestion(Long questionId);
    void submit(Long memberId, SurveySubmitRequest request);
}
