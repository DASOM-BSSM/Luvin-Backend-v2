package com.luvin.survey.service;

import com.luvin.survey.definition.SurveyDefinitionConfig;
import com.luvin.survey.definition.SurveyDefinitionConfigLoader;
import com.luvin.survey.definition.SurveyDefinitionQuestion;
import com.luvin.survey.domain.SurveyDefinitionV2;
import com.luvin.survey.dto.SurveyDefinitionResponse;
import com.luvin.survey.repository.SurveyDefinitionV2Repository;
import com.luvin.survey.service.exception.SurveyDefinitionUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SurveyDefinitionServiceImpl implements SurveyDefinitionService {

    private static final String TITLE = "나만의 반죽 만들기";
    private static final String ACTIVE = "active";

    private final SurveyDefinitionV2Repository surveyDefinitionV2Repository;

    @Override
    @Transactional(readOnly = true)
    public SurveyDefinitionResponse getCurrentDefinition() {
        SurveyDefinitionV2 definition = surveyDefinitionV2Repository.findFirstByStatusOrderByCreatedAtDesc(ACTIVE)
                .orElseThrow(SurveyDefinitionUnavailableException::new);
        SurveyDefinitionConfig config = SurveyDefinitionConfigLoader.parse(definition.getConfigJson());

        List<SurveyDefinitionResponse.QuestionItem> questions = new java.util.ArrayList<>();
        List<SurveyDefinitionQuestion> configQuestions = config.questions();
        for (int i = 0; i < configQuestions.size(); i++) {
            SurveyDefinitionQuestion q = configQuestions.get(i);
            List<SurveyDefinitionResponse.AnswerItem> answers = new java.util.ArrayList<>();
            for (int j = 0; j < q.answers().size(); j++) {
                var a = q.answers().get(j);
                answers.add(new SurveyDefinitionResponse.AnswerItem(a.id(), j + 1, a.text()));
            }
            questions.add(new SurveyDefinitionResponse.QuestionItem(q.id(), i + 1, q.text(), answers));
        }

        return new SurveyDefinitionResponse(
                definition.getId(), config.surveyVersion(), config.scoringVersion(), config.classificationVersion(),
                TITLE, questions.size(), questions);
    }
}
