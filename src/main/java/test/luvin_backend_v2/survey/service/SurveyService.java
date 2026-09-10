package com.luvin.survey;

import com.luvin.survey.dto.SurveyDetailResponse;
import com.luvin.survey.dto.SurveyListItemResponse;
import com.luvin.survey.dto.SurveyOptionResponse;
import com.luvin.survey.dto.SurveySubmitRequest;
import java.util.List;

public interface SurveyService {
    List<SurveyListItemResponse> getSurveys();
    SurveyDetailResponse getSurveyDetail(Long surveyId);
    SurveyOptionResponse getSurveyOptions(Long surveyId);
    void submit(Long memberId, Long surveyId, SurveySubmitRequest request);
}