package com.luvin.survey.repository;

import com.luvin.survey.domain.SurveyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {
    List<SurveyQuestion> findAllBySurvey_SurveyIdOrderByQuestionIdAsc(Long surveyId);
    Optional<SurveyQuestion> findFirstBySurvey_SurveyIdOrderByQuestionIdAsc(Long surveyId);
}
