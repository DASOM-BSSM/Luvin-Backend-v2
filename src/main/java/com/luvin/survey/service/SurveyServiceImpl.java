package com.luvin.survey.service;

import com.luvin.common.exception.SurveyOptionNotFoundException;
import com.luvin.common.exception.SurveyQuestionNotFoundException;
import com.luvin.survey.domain.SurveyAnswer;
import com.luvin.survey.domain.SurveyOption;
import com.luvin.survey.domain.SurveyQuestion;
import com.luvin.survey.dto.SurveyOptionResponse;
import com.luvin.survey.dto.SurveySubmitRequest;
import com.luvin.survey.repository.SurveyAnswerRepository;
import com.luvin.survey.repository.SurveyOptionRepository;
import com.luvin.survey.repository.SurveyQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SurveyServiceImpl implements SurveyService {

    private final SurveyQuestionRepository surveyQuestionRepository;
    private final SurveyOptionRepository surveyOptionRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;

    public SurveyServiceImpl(SurveyQuestionRepository surveyQuestionRepository,
                             SurveyOptionRepository surveyOptionRepository,
                             SurveyAnswerRepository surveyAnswerRepository) {
        this.surveyQuestionRepository = surveyQuestionRepository;
        this.surveyOptionRepository = surveyOptionRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyOptionResponse getQuestion(Long questionId) {
        SurveyQuestion question = surveyQuestionRepository.findById(questionId)
                .orElseThrow(() -> new SurveyQuestionNotFoundException(questionId));

        List<SurveyOptionResponse.OptionItem> options = surveyOptionRepository
                .findAllByQuestion_QuestionIdOrderByOptionIdAsc(question.getQuestionId()).stream()
                .map(o -> new SurveyOptionResponse.OptionItem(o.getOptionId(), o.getContent()))
                .collect(Collectors.toList());

        return new SurveyOptionResponse(question.getQuestionId(), question.getContent(), options);
    }

    @Override
    @Transactional
    public void submit(Long memberId, SurveySubmitRequest request) {
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
