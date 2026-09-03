package com.luvin.survey;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {
    List<SurveyQuestion> findAllBySurvey_SurveyIdOrderByQuestionIdAsc(Long surveyId);
    Optional<SurveyQuestion> findFirstBySurvey_SurveyIdOrderByQuestionIdAsc(Long surveyId);
}