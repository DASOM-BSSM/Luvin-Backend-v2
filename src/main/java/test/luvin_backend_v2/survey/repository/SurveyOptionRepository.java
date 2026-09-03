package com.luvin.survey;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SurveyOptionRepository extends JpaRepository<SurveyOption, Long> {
    List<SurveyOption> findAllByQuestion_QuestionIdOrderByOptionIdAsc(Long questionId);
}