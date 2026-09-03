package com.luvin.survey;

import com.luvin.common.exception.*;
import com.luvin.survey.dto.SurveyDetailResponse;
import com.luvin.survey.dto.SurveyListItemResponse;
import com.luvin.survey.dto.SurveyOptionResponse;
import com.luvin.survey.dto.SurveySubmitRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SurveyServiceImpl implements SurveyService {

    private final SurveyRepository surveyRepository;
    private final SurveyQuestionRepository surveyQuestionRepository;
    private final SurveyOptionRepository surveyOptionRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;

    public SurveyServiceImpl(SurveyRepository surveyRepository,
                             SurveyQuestionRepository surveyQuestionRepository,
                             SurveyOptionRepository surveyOptionRepository,
                             SurveyAnswerRepository surveyAnswerRepository) {
        this.surveyRepository = surveyRepository;
        this.surveyQuestionRepository = surveyQuestionRepository;
        this.surveyOptionRepository = surveyOptionRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SurveyListItemResponse> getSurveys() {
        return surveyRepository.findAll().stream()
                .map(SurveyListItemResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyDetailResponse getSurveyDetail(Long surveyId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new SurveyNotFoundException(surveyId));

        List<SurveyDetailResponse.QuestionItem> questions = surveyQuestionRepository
                .findAllBySurvey_SurveyIdOrderByQuestionIdAsc(surveyId).stream()
                .map(q -> new SurveyDetailResponse.QuestionItem(q.getQuestionId(), q.getContent()))
                .collect(Collectors.toList());

        return new SurveyDetailResponse(survey.getSurveyId(), survey.getTitle(), questions);
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyOptionResponse getSurveyOptions(Long surveyId) {
        if (!surveyRepository.existsById(surveyId)) {
            throw new SurveyNotFoundException(surveyId);
        }

        SurveyQuestion question = surveyQuestionRepository
                .findFirstBySurvey_SurveyIdOrderByQuestionIdAsc(surveyId)
                .orElseThrow(() -> new SurveyQuestionNotFoundException(null));

        List<SurveyOptionResponse.OptionItem> options = surveyOptionRepository
                .findAllByQuestion_QuestionIdOrderByOptionIdAsc(question.getQuestionId()).stream()
                .map(o -> new SurveyOptionResponse.OptionItem(o.getOptionId(), o.getContent()))
                .collect(Collectors.toList());

        return new SurveyOptionResponse(question.getQuestionId(), question.getContent(), options);
    }

    @Override
    @Transactional
    public void submit(Long memberId, Long surveyId, SurveySubmitRequest request) {
        if (!surveyRepository.existsById(surveyId)) {
            throw new SurveyNotFoundException(surveyId);
        }

        for (SurveySubmitRequest.AnswerItem answerItem : request.getAnswers()) {
            SurveyQuestion question = surveyQuestionRepository.findById(answerItem.getQuestionId())
                    .orElseThrow(() -> new SurveyQuestionNotFoundException(answerItem.getQuestionId()));

            SurveyOption option = surveyOptionRepository.findById(answerItem.getOptionId())
                    .orElseThrow(() -> new SurveyOptionNotFoundException(answerItem.getOptionId()));

            if (!option.getQuestion().getQuestionId().equals(question.getQuestionId())) {
                throw new SurveyOptionNotFoundException(answerItem.getOptionId());
            }

            surveyAnswerRepository.save(new SurveyAnswer(memberId, question, option));
        }
    }
}